#!/usr/bin/env python3
"""Builds minimal valid .dex fixtures for the LazyKt linkage gate self-test.

Not a general dex compiler: emits exactly the tables the gate inspects
(header, string/type/proto/method ids, class defs, class data with access
flags, no code). Sorted per the DEX spec so androguard parses them like real
R8 output. Each fixture APK is a zip of one or more classes*.dex.
"""
import hashlib
import struct
import zlib

ACC_PUBLIC = 0x1
ACC_STATIC = 0x8
ACC_INTERFACE = 0x200
ACC_ABSTRACT = 0x400

def uleb(n):
    out = bytearray()
    while True:
        b = n & 0x7F
        n >>= 7
        if n:
            out.append(b | 0x80)
        else:
            out.append(b)
            return bytes(out)

class DexBuilder:
    def __init__(self):
        self.classes = []  # dicts: name, super(None ok), access, methods=[(name, ret, params, access, direct?)]
        self.refs = []     # extra method_ids without definitions: (cls, name, ret, params)

    def add_class(self, name, super_name, access, methods):
        self.classes.append(dict(name=name, super=super_name, access=access, methods=methods))

    def add_ref(self, cls, name, ret, params):
        self.refs.append((cls, name, ret, params))

    def build(self):
        strings = set()
        protos = set()
        type_names = set()
        method_ids = []
        def add_method(cls_name, mname, ret, params):
            shorty = ('L' if ret.startswith('L') else ret) + ''.join('L' if p.startswith('L') else p for p in params)
            strings.update([cls_name, mname, ret, shorty])
            strings.update(params)
            type_names.update([cls_name, ret]); type_names.update(params)
            protos.add((ret, tuple(params), shorty))
            method_ids.append((cls_name, mname, ret, tuple(params)))
        for c in self.classes:
            strings.add(c['name']); type_names.add(c['name'])
            sup = c['super'] or 'Ljava/lang/Object;'
            strings.add(sup); type_names.add(sup)
            for (mn, ret, params, acc, direct) in c['methods']:
                add_method(c['name'], mn, ret, params)
        for (cls, mn, ret, params) in self.refs:
            add_method(cls, mn, ret, params)

        s_list = sorted(strings)
        s_idx = {s: i for i, s in enumerate(s_list)}
        t_list = sorted(type_names, key=lambda t: s_idx[t])
        t_idx = {t: i for i, t in enumerate(t_list)}
        p_list = sorted(protos, key=lambda p: (t_idx[p[0]], tuple(t_idx[x] for x in p[1])))
        p_idx = {p: i for i, p in enumerate(p_list)}
        m_list = sorted(set(method_ids), key=lambda m: (t_idx[m[0]], s_idx[m[1]], p_idx[(m[2], m[3], ('L' if m[2].startswith('L') else m[2]) + ''.join('L' if x.startswith('L') else x for x in m[3]))]))
        m_idx = {m: i for i, m in enumerate(m_list)}
        c_sorted = sorted(self.classes, key=lambda c: t_idx[c['name']])

        header_size = 0x70
        off = header_size
        def align4(o): return (o + 3) & ~3
        string_ids_off = off; off += 4 * len(s_list)
        type_ids_off = off; off += 4 * len(t_list)
        proto_ids_off = off; off += 12 * len(p_list)
        method_ids_off = off; off += 8 * len(m_list)
        class_defs_off = off; off += 32 * len(c_sorted)
        off = align4(off)
        data_off = off
        data = bytearray()
        data_offsets = {}
        # string_data
        string_data_offs = []
        for s in s_list:
            string_data_offs.append(data_off + len(data))
            b = s.encode('utf-8')
            data += uleb(len(s)) + b + b'\x00'
        # type_lists for proto params
        type_list_offs = {}
        for p in p_list:
            params = p[1]
            if params:
                if len(data) % 4: data += b'\x00' * (4 - len(data) % 4)
                type_list_offs[p] = data_off + len(data)
                data += struct.pack('<I', len(params))
                for x in params:
                    data += struct.pack('<H', t_idx[x])
                if len(params) % 2: data += b'\x00\x00'
        # class_data
        class_data_offs = {}
        for c in c_sorted:
            directs = [(mn, ret, prm, acc) for (mn, ret, prm, acc, d) in c['methods'] if d]
            virtuals = [(mn, ret, prm, acc) for (mn, ret, prm, acc, d) in c['methods'] if not d]
            class_data_offs[c['name']] = data_off + len(data)
            data += uleb(0) + uleb(0) + uleb(len(directs)) + uleb(len(virtuals))
            prev = 0
            for (mn, ret, prm, acc) in directs:
                i = m_idx[(c['name'], mn, ret, tuple(prm))]
                data += uleb(i - prev); prev = i
                data += uleb(acc) + uleb(0)
            prev = 0
            for (mn, ret, prm, acc) in virtuals:
                i = m_idx[(c['name'], mn, ret, tuple(prm))]
                data += uleb(i - prev); prev = i
                data += uleb(acc) + uleb(0)
        # map_list
        if len(data) % 4: data += b'\x00' * (4 - len(data) % 4)
        map_off = data_off + len(data)
        items = [(0x0000, len([1]), 0), (0x0001, len(s_list), string_ids_off), (0x0002, len(t_list), type_ids_off),
                 (0x0003, len(p_list), proto_ids_off), (0x0005, len(m_list), method_ids_off),
                 (0x0006, len(c_sorted), class_defs_off), (0x2002, len(s_list), string_data_offs[0] if s_list else 0)]
        if type_list_offs: items.append((0x1001, len(type_list_offs), min(type_list_offs.values())))
        items.append((0x2000, len(c_sorted), min(class_data_offs.values()) if c_sorted else 0))
        items.append((0x1000, 1, map_off))
        data += struct.pack('<I', len(items))
        for typ, size, o in items:
            data += struct.pack('<HHII', typ, 0, size, o)

        file_size = data_off + len(data)
        out = bytearray(file_size)
        out[0:8] = b'dex\n035\x00'
        out[32:36] = struct.pack('<I', file_size)
        out[36:40] = struct.pack('<I', header_size)
        out[40:44] = struct.pack('<I', 0x12345678)
        out[52:56] = struct.pack('<I', map_off)
        out[56:60] = struct.pack('<I', len(s_list)); out[60:64] = struct.pack('<I', string_ids_off)
        out[64:68] = struct.pack('<I', len(t_list)); out[68:72] = struct.pack('<I', type_ids_off)
        out[72:76] = struct.pack('<I', len(p_list)); out[76:80] = struct.pack('<I', proto_ids_off)
        out[80:84] = struct.pack('<I', 0); out[84:88] = struct.pack('<I', 0)  # field_ids
        out[88:92] = struct.pack('<I', len(m_list)); out[92:96] = struct.pack('<I', method_ids_off)
        out[96:100] = struct.pack('<I', len(c_sorted)); out[100:104] = struct.pack('<I', class_defs_off)
        out[104:108] = struct.pack('<I', len(data)); out[108:112] = struct.pack('<I', data_off)
        for i, so in enumerate(string_data_offs):
            struct.pack_into('<I', out, string_ids_off + 4 * i, so)
        for i, t in enumerate(t_list):
            struct.pack_into('<I', out, type_ids_off + 4 * i, s_idx[t])
        for i, p in enumerate(p_list):
            struct.pack_into('<III', out, proto_ids_off + 12 * i, s_idx[p[2]], t_idx[p[0]], type_list_offs.get(p, 0))
        for i, m in enumerate(m_list):
            struct.pack_into('<HHI', out, method_ids_off + 8 * i, t_idx[m[0]], p_idx[(m[2], m[3], ('L' if m[2].startswith('L') else m[2]) + ''.join('L' if x.startswith('L') else x for x in m[3]))], s_idx[m[1]])
        for i, c in enumerate(c_sorted):
            sup = c['super'] or 'Ljava/lang/Object;'
            struct.pack_into('<IIIIIIII', out, class_defs_off + 32 * i,
                             t_idx[c['name']], c['access'], t_idx[sup], 0, 0xFFFFFFFF, 0,
                             class_data_offs[c['name']], 0)
        out[data_off:] = bytes(data)
        out[12:32] = hashlib.sha1(bytes(out[32:])).digest()
        struct.pack_into('<I', out, 8, zlib.adler32(bytes(out[12:])) & 0xFFFFFFFF)
        return bytes(out)
