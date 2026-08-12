package com.civileg.app.utils

import com.civileg.app.domain.entities.SteelGrade

object SteelDictionary {

    // ============================================================
    // SECTION DICTIONARY - Egyptian Code (ECP 203-2018)
    // ============================================================
    
    val egyptianSections = mapOf(
        // Hot Rolled I Sections (IPE - European Standard)
        "IPE 80" to SectionInfo("IPE 80", "h=80mm, b=46mm, tw=3.8mm, tf=5.2mm", 6.0, "سِكة اوروبية"),
        "IPE 100" to SectionInfo("IPE 100", "h=100mm, b=55mm, tw=4.1mm, tf=5.7mm", 8.1, "سِكة اوروبية"),
        "IPE 120" to SectionInfo("IPE 120", "h=120mm, b=64mm, tw=4.4mm, tf=6.3mm", 10.4, "سِكة اوروبية"),
        "IPE 140" to SectionInfo("IPE 140", "h=140mm, b=73mm, tw=4.7mm, tf=6.9mm", 12.9, "سِكة اوروبية"),
        "IPE 160" to SectionInfo("IPE 160", "h=160mm, b=82mm, tw=5.0mm, tf=7.4mm", 15.8, "سِكة اوروبية"),
        "IPE 180" to SectionInfo("IPE 180", "h=180mm, b=91mm, tw=5.3mm, tf=8.0mm", 18.8, "سِكة اوروبية"),
        "IPE 200" to SectionInfo("IPE 200", "h=200mm, b=100mm, tw=5.6mm, tf=8.5mm", 22.4, "سِكة اوروبية"),
        "IPE 220" to SectionInfo("IPE 220", "h=220mm, b=110mm, tw=5.9mm, tf=9.2mm", 26.2, "سِكة اوروبية"),
        "IPE 240" to SectionInfo("IPE 240", "h=240mm, b=120mm, tw=6.2mm, tf=9.8mm", 30.7, "سِكة اوروبية"),
        "IPE 270" to SectionInfo("IPE 270", "h=270mm, b=135mm, tw=6.6mm, tf=10.2mm", 36.1, "سِكة اوروبية"),
        "IPE 300" to SectionInfo("IPE 300", "h=300mm, b=150mm, tw=7.1mm, tf=10.7mm", 42.2, "سِكة اوروبية"),
        "IPE 330" to SectionInfo("IPE 330", "h=330mm, b=160mm, tw=7.5mm, tf=11.5mm", 49.1, "سِكة اوروبية"),
        "IPE 360" to SectionInfo("IPE 360", "h=360mm, b=170mm, tw=8.0mm, tf=12.7mm", 57.1, "سِكة اوروبية"),
        "IPE 400" to SectionInfo("IPE 400", "h=400mm, b=180mm, tw=8.6mm, tf=13.5mm", 66.3, "سِكة اوروبية"),
        
        // Hot Rolled H Sections (HEA - European Wide Flange)
        "HEA 100" to SectionInfo("HEA 100", "h=96mm, b=100mm, tw=5mm, tf=8mm", 16.7, "عريض اوروبي"),
        "HEA 120" to SectionInfo("HEA 120", "h=114mm, b=120mm, tw=5mm, tf=8mm", 19.9, "عريض اوروبي"),
        "HEA 140" to SectionInfo("HEA 140", "h=133mm, b=140mm, tw=5.5mm, tf=8.5mm", 24.7, "عريض اوروبي"),
        "HEA 160" to SectionInfo("HEA 160", "h=152mm, b=160mm, tw=6mm, tf=9mm", 30.4, "عريض اوروبي"),
        "HEA 180" to SectionInfo("HEA 180", "h=171mm, b=180mm, tw=6mm, tf=9.5mm", 35.5, "عريض اوروبي"),
        "HEA 200" to SectionInfo("HEA 200", "h=190mm, b=200mm, tw=6.5mm, tf=10mm", 42.3, "عريض اوروبي"),
        "HEA 220" to SectionInfo("HEA 220", "h=210mm, b=220mm, tw=7mm, tf=11mm", 50.5, "عريض اوروبي"),
        "HEA 240" to SectionInfo("HEA 240", "h=230mm, b=240mm, tw=7.5mm, tf=12mm", 60.3, "عريض اوروبي"),
        "HEA 260" to SectionInfo("HEA 260", "h=250mm, b=260mm, tw=7.5mm, tf=12.5mm", 68.2, "عريض اوروبي"),
        "HEA 280" to SectionInfo("HEA 280", "h=270mm, b=280mm, tw=8mm, tf=13mm", 76.4, "عريض اوروبي"),
        "HEA 300" to SectionInfo("HEA 300", "h=290mm, b=300mm, tw=8.5mm, tf=14mm", 88.3, "عريض اوروبي"),
        "HEA 400" to SectionInfo("HEA 400", "h=390mm, b=300mm, tw=11mm, tf=19mm", 125.0, "عريض اوروبي"),
        
        // Hot Rolled H Sections (HEB - European Wide Flange)
        "HEB 100" to SectionInfo("HEB 100", "h=100mm, b=100mm, tw=6mm, tf=10mm", 20.4, "عريض اوروبي"),
        "HEB 120" to SectionInfo("HEB 120", "h=120mm, b=120mm, tw=6.5mm, tf=11mm", 26.7, "عريض اوروبي"),
        "HEB 140" to SectionInfo("HEB 140", "h=140mm, b=140mm, tw=7mm, tf=12mm", 33.7, "عريض اوروبي"),
        "HEB 160" to SectionInfo("HEB 160", "h=160mm, b=160mm, tw=8mm, tf=13mm", 42.6, "عريض اوروبي"),
        "HEB 180" to SectionInfo("HEB 180", "h=180mm, b=180mm, tw=8.5mm, tf=14mm", 51.2, "عريض اوروبي"),
        "HEB 200" to SectionInfo("HEB 200", "h=200mm, b=200mm, tw=9mm, tf=15mm", 61.3, "عريض اوروبي"),
        "HEB 220" to SectionInfo("HEB 220", "h=220mm, b=220mm, tw=9.5mm, tf=16mm", 71.5, "عريض اوروبي"),
        "HEB 240" to SectionInfo("HEB 240", "h=240mm, b=240mm, tw=10mm, tf=17mm", 83.2, "عريض اوروبي"),
        "HEB 260" to SectionInfo("HEB 260", "h=260mm, b=260mm, tw=10mm, tf=17.5mm", 93.0, "عريض اوروبي"),
        "HEB 300" to SectionInfo("HEB 300", "h=300mm, b=300mm, tw=11mm, tf=19mm", 117.0, "عريض اوروبي"),
        
        // Channels (UPN - European)
        "UPN 80" to SectionInfo("UPN 80", "h=80mm, b=45mm, tw=6mm, tf=8mm", 8.64, "قناة"),
        "UPN 100" to SectionInfo("UPN 100", "h=100mm, b=50mm, tw=6mm, tf=8.5mm", 10.6, "قناة"),
        "UPN 120" to SectionInfo("UPN 120", "h=120mm, b=55mm, tw=7mm, tf=9mm", 13.4, "قناة"),
        "UPN 140" to SectionInfo("UPN 140", "h=140mm, b=60mm, tw=7mm, tf=10mm", 16.0, "قناة"),
        "UPN 160" to SectionInfo("UPN 160", "h=160mm, b=65mm, tw=7.5mm, tf=10.5mm", 18.8, "قناة"),
        "UPN 180" to SectionInfo("UPN 180", "h=180mm, b=70mm, tw=8mm, tf=11mm", 22.0, "قناة"),
        "UPN 200" to SectionInfo("UPN 200", "h=200mm, b=75mm, tw=8.5mm, tf=11.5mm", 25.3, "قناة"),
        
        // Equal Angles
        "L 50x50x5" to SectionInfo("L 50×50×5", "b=50mm, t=5mm", 3.77, "زاوية متساوية"),
        "L 60x60x6" to SectionInfo("L 60×60×6", "b=60mm, t=6mm", 5.42, "زاوية متساوية"),
        "L 70x70x7" to SectionInfo("L 70×70×7", "b=70mm, t=7mm", 7.38, "زاوية متساوية"),
        "L 80x80x8" to SectionInfo("L 80×80×8", "b=80mm, t=8mm", 9.66, "زاوية متساوية"),
        "L 100x100x10" to SectionInfo("L 100×100×10", "b=100mm, t=10mm", 15.0, "زاوية متساوية"),
        
        // Plates
        "PL 150x6" to SectionInfo("PL 150×6", "b=150mm, t=6mm", 7.07, "لوح"),
        "PL 200x8" to SectionInfo("PL 200×8", "b=200mm, t=8mm", 12.57, "لوح"),
        "PL 250x10" to SectionInfo("PL 250×10", "b=250mm, t=10mm", 19.63, "لوح"),
        "PL 300x12" to SectionInfo("PL 300×12", "b=300mm, t=12mm", 28.27, "لوح")
    )
    
    // ============================================================
    // SECTION DICTIONARY - American Code (AISC LRFD)
    // ============================================================
    
    val americanSections = mapOf(
        // W-Shapes (Wide Flange)
        "W 6x9" to SectionInfo("W6×9", "h=150mm, bf=100mm, tw=4.3mm, tf=5.8mm", 13.5, "عريض امريكي"),
        "W 6x12" to SectionInfo("W6×12", "h=153mm, bf=102mm, tw=5.8mm, tf=7.1mm", 17.9, "عريض امريكي"),
        "W 6x16" to SectionInfo("W6×16", "h=160mm, bf=103mm, tw=7.2mm, tf=9.3mm", 23.8, "عريض امريكي"),
        "W 8x10" to SectionInfo("W8×10", "h=200mm, bf=100mm, tw=4.3mm, tf=6.2mm", 15.0, "عريض امريكي"),
        "W 8x15" to SectionInfo("W8×15", "h=206mm, bf=102mm, tw=5.8mm, tf=7.9mm", 22.4, "عريض امريكي"),
        "W 8x18" to SectionInfo("W8×18", "h=207mm, bf=133mm, tw=5.8mm, tf=8.4mm", 26.8, "عريض امريكي"),
        "W 8x24" to SectionInfo("W8×24", "h=201mm, bf=165mm, tw=7.5mm, tf=10.2mm", 35.7, "عريض امريكي"),
        "W 10x12" to SectionInfo("W10×12", "h=200mm, bf=100mm, tw=4.3mm, tf=5.2mm", 17.9, "عريض امريكي"),
        "W 10x19" to SectionInfo("W10×19", "h=210mm, bf=134mm, tw=6.2mm, tf=7.9mm", 28.3, "عريض امريكي"),
        "W 10x22" to SectionInfo("W10×22", "h=206mm, bf=178mm, tw=6.2mm, tf=7.4mm", 32.7, "عريض امريكي"),
        "W 10x30" to SectionInfo("W10×30", "h=310mm, bf=100mm, tw=5.8mm, tf=4.9mm", 44.6, "عريض امريكي"),
        "W 10x39" to SectionInfo("W10×39", "h=310mm, bf=125mm, tw=6.0mm, tf=7.2mm", 58.0, "عريض امريكي"),
        "W 12x14" to SectionInfo("W12×14", "h=200mm, bf=100mm, tw=3.7mm, tf=4.3mm", 20.8, "عريض امريكي"),
        "W 12x19" to SectionInfo("W12×19", "h=200mm, bf=148mm, tw=4.3mm, tf=5.0mm", 28.3, "عريض امريكي"),
        "W 12x26" to SectionInfo("W12×26", "h=207mm, bf=133mm, tw=5.8mm, tf=6.3mm", 38.8, "عريض امريكي"),
        "W 12x35" to SectionInfo("W12×35", "h=310mm, bf=102mm, tw=5.8mm, tf=6.1mm", 52.1, "عريض امريكي"),
        "W 12x53" to SectionInfo("W12×53", "h=310mm, bf=165mm, tw=7.7mm, tf=9.7mm", 78.9, "عريض امريكي"),
        "W 14x22" to SectionInfo("W14×22", "h=350mm, bf=127mm, tw=4.8mm, tf=5.6mm", 32.7, "عريض امريكي"),
        "W 14x30" to SectionInfo("W14×30", "h=350mm, bf=171mm, tw=4.8mm, tf=6.9mm", 44.6, "عريض امريكي"),
        "W 14x38" to SectionInfo("W14×38", "h=360mm, bf=170mm, tw=6.3mm, tf=8.0mm", 56.5, "عريض امريكي"),
        "W 14x53" to SectionInfo("W14×53", "h=350mm, bf=203mm, tw=7.7mm, tf=10.8mm", 78.8, "عريض امريكي"),
        "W 14x74" to SectionInfo("W14×74", "h=360mm, bf=205mm, tw=9.0mm, tf=13.8mm", 110.1, "عريض امريكي"),
        "W 16x26" to SectionInfo("W16×26", "h=400mm, bf=140mm, tv=4.4mm, tf=5.6mm", 38.7, "عريض امريكي"),
        "W 16x31" to SectionInfo("W16×31", "h=400mm, bf=155mm, tw=5.0mm, tf=6.2mm", 46.1, "عريض امريكي"),
        "W 16x40" to SectionInfo("W16×40", "h=403mm, bf=177mm, tw=5.5mm, tf=7.5mm", 59.5, "عريض امريكي"),
        "W 16x50" to SectionInfo("W16×50", "h=410mm, bf=179mm, tw=6.2mm, tf=9.0mm", 74.4, "عريض امريكي"),
        "W 18x35" to SectionInfo("W18×35", "h=450mm, bf=152mm, tw=5.3mm, tf=6.6mm", 52.1, "عريض امريكي"),
        "W 18x40" to SectionInfo("W18×40", "h=450mm, bf=170mm, tw=5.1mm, tf=6.5mm", 59.5, "عريض امريكي"),
        "W 18x50" to SectionInfo("W18×50", "h=455mm, bf=171mm, tw=6.0mm, tf=8.0mm", 74.4, "عريض امريكي"),
        "W 18x60" to SectionInfo("W18×60", "h=463mm, bf=178mm, tw=7.0mm, tf=9.7mm", 89.3, "عريض امريكي"),
        "W 21x44" to SectionInfo("W21×44", "h=525mm, bf=165mm, tw=5.6mm, tf=6.9mm", 65.5, "عريض امريكي"),
        "W 21x50" to SectionInfo("W21×50", "h=525mm, bf=180mm, tw=5.6mm, tf=7.5mm", 74.4, "عريض امريكي"),
        "W 21x57" to SectionInfo("W21×57", "h=530mm, bf=181mm, tw=6.4mm, tf=8.4mm", 84.8, "عريض امريكي"),
        "W 21x73" to SectionInfo("W21×73", "h=540mm, bf=189mm, tw=8.4mm, tf=10.9mm", 108.6, "عريض امريكي"),
        
        // M-Shapes
        "M 12x11.8" to SectionInfo("M12×11.8", "h=301mm, bf=96mm, tw=3.2mm, tf=4.3mm", 17.5, "متوسط"),
        
        // S-Shapes (Standard)
        "S 6x12.5" to SectionInfo("S6×12.5", "h=152mm, fb=56mm, tw=5.1mm, tf=6.1mm", 18.6, "قياسي"),
        "S 8x18.4" to SectionInfo("S8×18.4", "h=203mm, bf=70mm, tw=5.8mm, tf=7.7mm", 27.4, "قياسي"),
        
        // Channels (C)
        "C 3x5" to SectionInfo("C3×5", "h=76mm, bf=35mm, tw=3.3mm, tf=6.1mm", 7.4, "قناة"),
        "C 4x7.25" to SectionInfo("C4×7.25", "h=102mm, bf=40mm, tw=4.1mm, tf=7.2mm", 10.8, "قناة"),
        "C 5x9" to SectionInfo("C5×9", "h=127mm, bf=45mm, tw=4.7mm, tf=7.4mm", 13.4, "قناة"),
        "C 6x13" to SectionInfo("C6×13", "h=153mm, bf=51mm, tw=5.1mm, tf=8.1mm", 19.3, "قناة"),
        "C 7x14.75" to SectionInfo("C7×14.75", "h=178mm, bf=54mm, tw=5.3mm, tf=8.2mm", 21.9, "قناة"),
        "C 8x18.75" to SectionInfo("C8×18.75", "h=203mm, bf=59mm, tw=5.6mm, tf=9.0mm", 27.9, "قناة"),
        
        // MC Channels
        "MC 6x12" to SectionInfo("MC6×12", "h=152mm, bf=41mm, tw=4.8mm, tf=6.9mm", 17.8, "قناة امريكي"),
        "MC 8x21" to SectionInfo("MC8×21", "h=203mm, bf=59mm, tw=5.6mm, tf=8.1mm", 31.3, "قناة امريكي"),
        
        // Angles (L)
        "L 3x3x1/4" to SectionInfo("L3×3×1/4", "b=76mm, t=6.4mm", 3.67, "زاوية"),
        "L 4x4x1/4" to SectionInfo("L4×4×1/4", "b=102mm, t=6.4mm", 4.9, "زاوية"),
        "L 6x6x3/8" to SectionInfo("L6×6×3/8", "b=152mm, t=9.5mm", 11.1, "زاوية"),
        "L 8x8x1/2" to SectionInfo("L8×8×1/2", "b=203mm, t=12.7mm", 19.8, "زاوية"),
        
        // WT, MT, ST Shapes (Tees)
        "WT 7x19" to SectionInfo("WT7×19", "h=175mm, bf=95mm, tw=5.1mm, tf=8.0mm", 28.3, "تي شاه"),
        "WT 12x38" to SectionInfo("WT12×38", "h=300mm, bf=91mm, tw=7.7mm, tf=10.3mm", 56.5, "تي شاه")
    )
    
    // ============================================================
    // SECTION DICTIONARY - Saudi Code (SBC 301)
    // ============================================================
    
    val saudiSections = mapOf(
        // Same IPE as Egyptian (European standard)
        "IPE 100" to SectionInfo("IPE 100", "h=100mm, b=55mm", 8.1, "سِكة اوروبية"),
        "IPE 120" to SectionInfo("IPE 120", "h=120mm, b=64mm", 10.4, "سِكة اوروبية"),
        "IPE 140" to SectionInfo("IPE 140", "h=140mm, b=73mm", 12.9, "سِكة اوروبية"),
        "IPE 160" to SectionInfo("IPE 160", "h=160mm, b=82mm", 15.8, "سِكة اوروبية"),
        "IPE 180" to SectionInfo("IPE 180", "h=180mm, b=91mm", 18.8, "سِكة اوروبية"),
        "IPE 200" to SectionInfo("IPE 200", "h=200mm, b=100mm", 22.4, "سِكة اوروبية"),
        "IPE 220" to SectionInfo("IPE 220", "h=220mm, b=110mm", 26.2, "سِكة اوروبية"),
        "IPE 240" to SectionInfo("IPE 240", "h=240mm, b=120mm", 30.7, "سِكة اوروبية"),
        "IPE 300" to SectionInfo("IPE 300", "h=300mm, b=150mm", 42.2, "سِكة اوروبية"),
        
        // HEA for Saudi (also European)
        "HEA 100" to SectionInfo("HEA 100", "h=96mm, b=100mm", 16.7, "عريض"),
        "HEA 120" to SectionInfo("HEA 120", "h=114mm, b=120mm", 19.9, "عريض"),
        "HEA 140" to SectionInfo("HEA 140", "h=133mm, b=140mm", 24.7, "عريض"),
        "HEA 160" to SectionInfo("HEA 160", "h=152mm, b=160mm", 30.4, "عريض"),
        "HEA 180" to SectionInfo("HEA 180", "h=171mm, b=180mm", 35.5, "عريض"),
        "HEA 200" to SectionInfo("HEA 200", "h=190mm, b=200mm", 42.3, "عريض"),
        "HEA 220" to SectionInfo("HEA 220", "h=210mm, b=220mm", 50.5, "عريض"),
        "HEA 240" to SectionInfo("HEA 240", "h=230mm, b=240mm", 60.3, "عريض"),
        "HEA 260" to SectionInfo("HEA 260", "h=250mm, b=260mm", 68.2, "عريض"),
        "HEA 280" to SectionInfo("HEA 280", "h=270mm, b=280mm", 76.4, "عريض"),
        "HEA 300" to SectionInfo("HEA 300", "h=290mm, b=300mm", 88.3, "عريض"),
        "HEA 350" to SectionInfo("HEA 350", "h=340mm, b=300mm", 105.0, "عريض"),
        "HEA 400" to SectionInfo("HEA 400", "h=390mm, b=300mm", 125.0, "عريض"),
        
        // HEB for Saudi
        "HEB 100" to SectionInfo("HEB 100", "h=100mm, b=100mm", 20.4, "عريض"),
        "HEB 120" to SectionInfo("HEB 120", "h=120mm, b=120mm", 26.7, "عريض"),
        "HEB 140" to SectionInfo("HEB 140", "h=140mm, b=140mm", 33.7, "عريض"),
        "HEB 160" to SectionInfo("HEB 160", "h=160mm, b=160mm", 42.6, "عريض"),
        "HEB 180" to SectionInfo("HEB 180", "h=180mm, b=180mm", 51.2, "عريض"),
        "HEB 200" to SectionInfo("HEB 200", "h=200mm, b=200mm", 61.3, "عريض"),
        "HEB 220" to SectionInfo("HEB 220", "h=220mm, b=220mm", 71.5, "عريض"),
        "HEB 240" to SectionInfo("HEB 240", "h=240mm, b=240mm", 83.2, "عريض"),
        "HEB 260" to SectionInfo("HEB 260", "h=260mm, b=260mm", 93.0, "عريض"),
        "HEB 280" to SectionInfo("HEB 280", "h=280mm, b=280mm", 103.0, "عريض"),
        "HEB 300" to SectionInfo("HEB 300", "h=300mm, b=300mm", 117.0, "عريض"),
        "HEB 350" to SectionInfo("HEB 350", "h=350mm, b=300mm", 137.0, "عريض"),
        "HEB 400" to SectionInfo("HEB 400", "h=400mm, b=300mm", 155.0, "عريض"),
        
        // Plates (لوح)
        "PL 100x6" to SectionInfo("PL 100×6", "عرض=100مم, سمك=6مم", 4.71, "لوح"),
        "PL 150x8" to SectionInfo("PL 150×8", "عرض=150مم, سمك=8مم", 9.42, "لوح"),
        "PL 200x10" to SectionInfo("PL 200×10", "عرض=200مم, سمك=10مم", 15.71, "لوح"),
        "PL 250x12" to SectionInfo("PL 250×12", "عرض=250مم, سمك=12مم", 23.56, "لوح"),
        "PL 300x15" to SectionInfo("PL 300×15", "عرض=300مم, سمك=15مم", 35.34, "لوح"),
        
        // Angles
        "L 50x50x5" to SectionInfo("L 50×50×5", "b=50مم, t=5مم", 3.77, "زاوية"),
        "L 60x60x6" to SectionInfo("L 60×60×6", "b=60مم, t=6مم", 5.42, "زاوية"),
        "L 70x70x7" to SectionInfo("L 70×70×7", "b=70مم, t=7مم", 7.38, "زاوية"),
        "L 80x80x8" to SectionInfo("L 80×80×8", "b=80مم, t=8مم", 9.66, "زاوية"),
        "L 100x100x10" to SectionInfo("L 100×100×10", "b=100مم, t=10مم", 15.0, "زاوية")
    )
    
    // ============================================================
    // GLOSSARY OF TERMS
    // ============================================================
    
    val glossary = mapOf(
        "Modulus of Elasticity (E)" to mapOf(
            "en" to "Standard value is 210,000 MPa (200 GPa)",
            "ar" to "قيمة معيارية 210000 ميجاباسكال (200 جيجاباسكال)"
        ),
        "Yield Strength (Fy)" to mapOf(
            "en" to "The stress at which a material begins to deform plastically. For structural steel: Fy = 250 MPa (St-37) or 350 MPa (St-52)",
            "ar" to "الإجهاد الذي يبدأ عنده المادة في التشوه اللدن. للحديد الإنشائي: إجهاد الخضوع 250 ميجاباسكال (ستانليس 37) أو 350 ميجاباسكال (ستانليس 52)"
        ),
        "Ultimate Strength (Fu)" to mapOf(
            "en" to "The maximum stress the material can withstand before failure. For structural steel: Fu = 360-440 MPa",
            "ar" to "الإجهاد الأقصى الذي تتحمله المادة قبل الانهيار. للحديد الإنشائي: 360-440 ميجاباسكال"
        ),
        "Slenderness Ratio (λ)" to mapOf(
            "en" to "The ratio of the effective length of a member to its least radius of gyration (L/r)",
            "ar" to "نسبة الطول الفعال للعضو إلى أقل نصف قطر دوران (L/r)"
        ),
        "Compact Section" to mapOf(
            "en" to "Section that can develop its full plastic moment without local buckling",
            "ar" to "قطاع يستطيع تطوير عزمه البلاستيكي الكامل دون الانبعاج الموضعي"
        ),
        "Lateral Torsional Buckling" to mapOf(
            "en" to "Buckling of a beam due to the compression flange becoming unstable laterally",
            "ar" to "انبعاج عارضة بسبب عدم استقرار شفة الضغط جانبياً"
        ),
        "Stiffener" to mapOf(
            "en" to "A plate welded to the web or flange to prevent local buckling or reinforce connection",
            "ar" to "لوح ملحوم بال alma أو الشفة لمنع الانبعاج الموضعي أو تعزيز الوصلة"
        ),
        "Fillet Weld" to mapOf(
            "en" to "Most common weld type, triangular cross-section connecting two perpendicular surfaces",
            "ar" to "أكثر أنواع اللحام شيوعاً، مقطع مثلثي يربط سطحين متعامدين"
        ),
        "Butt Weld" to mapOf(
            "en" to "Weld connecting two parts in the same plane, for tension members",
            "ar" to "لحام يربط جزأين في نفس المستوى، لأعضاء الشد"
        ),
        "Prying Force (Q)" to mapOf(
            "en" to "Additional force introduced in bolted connections due to flexibility of connected parts",
            "ar" to "قوة إضافية تظهر في الوصلات البراغي بسبب مرونة الأجزاء المتصلة"
        ),
        "Block Shear" to mapOf(
            "en" to "Failure mode where a block of material tears out along shear failure paths",
            "ar" to "نمط الانهيار حيث ينفصل كتلة من المادة على طول مسارات قص_failure"
        ),
        "Net Section" to mapOf(
            "en" to "Cross-sectional area after deducting holes and openings",
            "ar" to "المساحة بعد طرح الثقوب والفتحات"
        ),
        "Gross Section" to mapOf(
            "en" to "Total cross-sectional area before any deductions",
            "ar" to "المطقة العرضية الإجمالية قبل أي خصومات"
        ),
        "Effective Length (Le)" to mapOf(
            "en" to "Length used for buckling analysis, based on end conditions",
            "ar" to "الطول الفعال المستخدم في تحليل الانبعاج، بناءً على conditions"
        ),
        "Moment Gradient Coefficient (Cb)" to mapOf(
            "en" to "Factor accounting for non-uniform moment distribution along beam",
            "ar" to "معامل يأخذ في الاعتبار توزيع العزم غير المت.uniform along beam"
        )
    )
    
    // ============================================================
    // WELDING DATA
    // ============================================================
    
    val weldingData = mapOf(
        "Fillet Weld Sizes" to mapOf(
            "en" to "Standard sizes: 3, 4, 5, 6, 8, 10, 12 mm",
            "ar" to "الأحجام القياسية: 3، 4، 5، 6، 8، 10، 12 ملم"
        ),
        "Min Fillet Size" to mapOf(
            "en" to "t = 3mm for plates < 6mm thick, otherwise t = 5mm",
            "ar" to "ص = 3مم للوح أقل من 6مم، خلاف ذلك ص = 5مم"
        ),
        "Max Fillet Size" to mapOf(
            "en" to "t = min(plate thickness) - 1.5mm",
            "ar" to "ص = min(سمك اللوح) - 1.5مم"
        ),
        "Electrode" to mapOf(
            "en" to "E7018 for structural steel (low hydrogen)",
            "ar" to "E7018 للحديد الإنشائي (منخفض الهيدروجين)"
        ),
        "AWS D1.1" to mapOf(
            "en" to "American Welding Society Structural Welding Code",
            "ar" to "كود اللحام الإنشائي الأمريكي"
        )
    )
    
    // ============================================================
    // BOLT DATA
    // ============================================================
    
    val boltData = mapOf(
        // Bolt diameters
        "M12" to mapOf("d" to "12mm", "An" to "84mm²", "Ar" to "113mm²"),
        "M16" to mapOf("d" to "16mm", "An" to "157mm²", "Ar" to "201mm²"),
        "M20" to mapOf("d" to "20mm", "An" to "245mm²", "Ar" to "314mm²"),
        "M22" to mapOf("d" to "22mm", "An" to "303mm²", "Ar" to "380mm²"),
        "M24" to mapOf("d" to "24mm", "An" to "353mm²", "Ar" to "452mm²"),
        "M27" to mapOf("d" to "27mm", "An" to "459mm²", "Ar" to "573mm²"),
        "M30" to mapOf("d" to "30mm", "An" to "561mm²", "Ar" to "707mm²"),
        
        // Bolt grades
        "4.6" to mapOf("Fy" to "240MPa", "Fu" to "400MPa", "en" to "Grade 4.6 (ordinary bolt)"),
        "8.8" to mapOf("Fy" to "640MPa", "Fu" to "800MPa", "en" to "Grade 8.8 (high strength)"),
        "10.9" to mapOf("Fy" to "900MPa", "Fu" to "1000MPa", "en" to "Grade 10.9 (high strength)"),
        
        // Bolt shear capacity
        "Shear 8.8" to mapOf("en" to "0.60 x Fu x Ab (for shear plane at bolt shank)"),
        "Bearing" to mapOf("en" to "1.5 x Fu x d x t (for each shear plane)")
    )
    
    // ============================================================
    // FUNCTIONS
    // ============================================================
    
    fun getSectionByCode(code: DesignCodeType): Map<String, SectionInfo> = when(code) {
        DesignCodeType.ECP -> egyptianSections
        DesignCodeType.ACI -> americanSections
        DesignCodeType.SBC -> saudiSections
    }
    
    fun getGlossaryTerm(term: String, language: String = "ar"): String? {
        val termData = glossary[term] ?: return null
        return when(language) {
            "ar" -> (termData as? Map<String, String>)?.get("ar")
            else -> (termData as? Map<String, String>)?.get("en")
        } ?: termData.toString()
    }
    
    fun getAllSectionsByCode(code: DesignCodeType): List<SectionInfo> {
        return getSectionByCode(code).values.toList()
    }
    
    // ============================================================
    // DATA CLASSES
    // ============================================================
    
    data class SectionInfo(
        val name: String,
        val dimensions: String,
        val weightKgM: Double,
        val descriptionAr: String
    )
    
    enum class DesignCodeType {
        ECP,    // Egyptian Code
        ACI,    // American Code  
        SBC     // Saudi Building Code
    }

    /**
     * مكتبة القطاعات المركبة (Built-up Sections)
     */
    fun getBuiltUpDescription(): String {
        return """
            القطاعات المركبة (Built-up Sections):
            تستخدم عندما لا تكفي القطاعات المدرفلة (Hot-rolled) للأحمال الكبيرة.
            - يتم تصميمها باستخدام ألواح (Plates) للحصول على Web و Flanges بأبعاد مخصصة.
            - يجب التحقق من اللحام الطولي (Longitudinal Weld) بين العصب والشفة.
        """.trimIndent()
    }

    /**
     * أنواع الوصلات الشائعة
     */
    val connectionTypes = listOf(
        "Fin Plate Connection (Shear only)",
        "End Plate Connection (Moment/Shear)",
        "Splice Connection",
        "Base Plate with Anchor Bolts",
        "Gusset Plate for Bracing"
    )
}
