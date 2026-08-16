package com.civileg.app.utils

import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Locale
import kotlin.math.*

/**
 * CIVILEG CAD ENGINE V7
 *
 * Full reinforcement-CAD layer over CalculatorDetailingV4.
 * - Real bar-shape geometry: STRAIGHT/L/U/C/stirrups/crossties/hoops/CUSTOM segments.
 * - Bar marks, lap tags, anchorage tags, cutting-length labels.
 * - Member sheets: elevation/plan/section/detail/BBS.
 * - Project index, master BBS, revision table, CAD QA.
 * - No design values are invented here; all design/detailing values come from CalculatorDetailingV4.
 * Units: mm.
 */
object CalculatorCadExporterV7 {

    enum class Paper(val width: Double, val height: Double) {
        A3(420.0, 297.0), A2(594.0, 420.0), A1(841.0, 594.0)
    }

    data class Settings(
        val projectName: String = "CIVILEG STRUCTURAL DESIGN",
        val client: String = "",
        val consultant: String = "",
        val contractor: String = "",
        val preparedBy: String = "",
        val checkedBy: String = "",
        val approvedBy: String = "",
        val revision: String = "00",
        val code: String = "ECP 203 / SBC / ACI",
        val paper: Paper = Paper.A3,
        val scaleLabel: String = "1:20",
        val includeBbsSheet: Boolean = true,
        val includeIndex: Boolean = true,
        val includeQa: Boolean = true
    )

    data class Sheet(val number: String, val title: String, val file: File, val memberId: String)
    data class Qa(val passed: Boolean, val sheets: Int, val bars: Int, val steelKg: Double, val issues: List<String>, val generatedAt: String)
    data class ProjectExport(val directory: File, val sheets: List<Sheet>, val index: File?, val masterBbs: File?, val qaFile: File?, val qa: Qa)

    private class W(val settings: Settings) {
        val sb = StringBuilder(1 shl 20)
        var entities = 0; var lines = 0; var circles = 0; var polylines = 0; var arcs = 0; var texts = 0; var dimensions = 0
        val layers = linkedSetOf<String>(); val marks = linkedSetOf<String>(); val box = doubleArrayOf(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY)
        var handle = 0x100
        fun h() = (handle++).toString(16).uppercase(Locale.US)
        fun raw(s:String){sb.append(s)}
        fun add(x:Double,y:Double){box[0]=min(box[0],x);box[1]=min(box[1],y);box[2]=max(box[2],x);box[3]=max(box[3],y)}
        fun start(type:String,layer:String,color:Int?=null,weight:Int?=null){raw("0\n${type}\n5\n${h()}\n100\nAcDbEntity\n8\n$layer\n");layers+=layer
            color?.let{code(62,it)};weight?.let{code(370,it)}
            when(type){"LINE"->raw("100\nAcDbLine\n");"CIRCLE"->raw("100\nAcDbCircle\n");"ARC"->raw("100\nAcDbCircle\n100\nAcDbArc\n");"LWPOLYLINE"->raw("100\nAcDbPolyline\n");"TEXT"->raw("100\nAcDbText\n");"MTEXT"->raw("100\nAcDbMText\n")}
            entities++
        }
        fun line(x1:Double,y1:Double,x2:Double,y2:Double,layer:String="TEXT",color:Int?=null,weight:Int=18){start("LINE",layer,color,weight);codeD(10,x1);codeD(20,y1);codeD(30,0.0);codeD(11,x2);codeD(21,y2);codeD(31,0.0);add(x1,y1);add(x2,y2);lines++}
        fun circle(x:Double,y:Double,r:Double,layer:String="TEXT",color:Int?=null,weight:Int=18){start("CIRCLE",layer,color,weight);codeD(10,x);codeD(20,y);codeD(30,0.0);codeD(40,r);add(x-r,y-r);add(x+r,y+r);circles++}
        fun arc(x:Double,y:Double,r:Double,a1:Double,a2:Double,layer:String="REBAR",color:Int?=null,weight:Int=18){start("ARC",layer,color,weight);codeD(10,x);codeD(20,y);codeD(30,0.0);codeD(40,r);codeD(50,a1);codeD(51,a2);add(x-r,y-r);add(x+r,y+r);arcs++}
        fun rect(x:Double,y:Double,w:Double,h:Double,layer:String="TEXT",color:Int?=null,weight:Int=18)=poly(listOf(x to y,(x+w) to y,(x+w) to (y+h),x to (y+h)),layer,true,color,weight)
        fun poly(points:List<Pair<Double,Double>>,layer:String="REBAR",closed:Boolean=false,color:Int?=null,weight:Int=18){if(points.size<2)return;start("LWPOLYLINE",layer,color,weight);code(90,points.size);code(70,if(closed)1 else 0);codeD(38,0.0);points.forEach{(x,y)->codeD(10,x);codeD(20,y);add(x,y)};polylines++}
        fun text(x:Double,y:Double,s:String,h:Double=3.0,layer:String="TEXT",color:Int?=null){start("TEXT",layer,color);codeD(10,x);codeD(20,y);codeD(30,0.0);codeD(40,h);code(1,s.replace("\n"," "));add(x,y);add(x+s.length*h*.42,y+h);texts++}
        fun mtext(x:Double,y:Double,lines:List<String>,h:Double=3.0,layer:String="TEXT"){start("MTEXT",layer);codeD(10,x);codeD(20,y);codeD(30,0.0);codeD(40,h);code(71,1);code(1,lines.joinToString("\\P").replace("\n"," "));add(x,y);add(x+100,y+lines.size*h);texts++}
        fun dim(x1:Double,y1:Double,x2:Double,y2:Double,offset:Double,label:String){val dx=x2-x1;val dy=y2-y1;val l=hypot(dx,dy);if(l<=1e-9)return;val nx=-dy/l;val ny=dx/l;val ax=x1+nx*offset;val ay=y1+ny*offset;val bx=x2+nx*offset;val by=y2+ny*offset;line(x1,y1,ax,ay,"DIM",5,13);line(x2,y2,bx,by,"DIM",5,13);line(ax,ay,bx,by,"DIM",5,13);arrow(ax,ay,atan2(dy,dx));arrow(bx,by,atan2(dy,dx)+PI);text((ax+bx)/2+nx*3,(ay+by)/2+ny*3,label,2.6,"DIM",5);dimensions++}
        fun arrow(x:Double,y:Double,a:Double){val z=6.0;line(x,y,x-z*cos(a-PI/6),y-z*sin(a-PI/6),"DIM",5,13);line(x,y,x-z*cos(a+PI/6),y-z*sin(a+PI/6),"DIM",5,13)}
        fun code(k:Int,v:Any){raw("$k\n$v\n")}; fun codeD(k:Int,v:Double){raw("$k\n${fmt(v)}\n")}
    }

    fun exportProject(packages:List<CalculatorDetailingV4.DetailingPackage>, bbs:CalculatorDetailingV4.BarSchedule, outDir:String, s:Settings=Settings()):ProjectExport{
        require(packages.isNotEmpty()); val dir=File(outDir).apply{mkdirs()}; val issues=mutableListOf<String>(); validate(packages,bbs,issues)
        val sheets=mutableListOf<Sheet>()
        packages.forEachIndexed{idx,pkg->
            val no="S-${(idx+1).toString().padStart(2,'0')}"
            val fn=File(dir,"${no}_${safe(pkg.memberId)}_${safe(title(pkg.memberType))}.dxf")
            try{writeMember(pkg,bbs,no,fn,s); sheets+=Sheet(no,title(pkg.memberType),fn,pkg.memberId)}catch(t:Throwable){issues+="${no}/${pkg.memberId}: ${t.message ?: "CAD generation failed"}"}
        }
        val bbsFile=if(s.includeBbsSheet)writeBbsSheet(dir,bbs,s) else null
        val idxFile=if(s.includeIndex)writeIndex(dir,sheets,s,bbsFile) else null
        if(s.includeQa)issues += sheets.flatMap{sheet->qaScan(sheet.file)}
        val qa=Qa(issues.isEmpty(),sheets.size,bbs.rows.size,bbs.totalWeightKg,issues.distinct(),Instant.now().toString())
        val qf=if(s.includeQa)writeQa(dir,qa,s) else null
        return ProjectExport(dir,sheets,idxFile,bbsFile,qf,qa)
    }

    private fun writeMember(pkg:CalculatorDetailingV4.DetailingPackage,bbs:CalculatorDetailingV4.BarSchedule,no:String,file:File,s:Settings){
        val w=W(s); header(w); classes(w); tables(w); blocksOnly(w); w.raw("0\nSECTION\n2\nENTITIES\n")
        frame(w,s,no,pkg)
        val baseX=20.0; val baseY=45.0; val availW=s.paper.width-290.0; val availH=s.paper.height-75.0
        when(pkg.memberType){
            CalculatorDetailingV4.MemberType.BEAM->beam(w,pkg,baseX,baseY,availW*.63,availH*.62)
            CalculatorDetailingV4.MemberType.COLUMN->column(w,pkg,baseX,baseY,availW*.63,availH*.62)
            CalculatorDetailingV4.MemberType.SLAB->slab(w,pkg,baseX,baseY,availW*.63,availH*.62)
            CalculatorDetailingV4.MemberType.FOOTING->footing(w,pkg,baseX,baseY,availW*.63,availH*.62)
            CalculatorDetailingV4.MemberType.WALL->wall(w,pkg,baseX,baseY,availW*.63,availH*.62)
            CalculatorDetailingV4.MemberType.TANK->tank(w,pkg,baseX,baseY,availW*.63,availH*.62)
            CalculatorDetailingV4.MemberType.STAIR->stair(w,pkg,baseX,baseY,availW*.63,availH*.62)
            CalculatorDetailingV4.MemberType.STEEL_MEMBER->steel(w,pkg,baseX,baseY,availW*.63,availH*.62)
            CalculatorDetailingV4.MemberType.CONNECTION->connection(w,pkg,baseX,baseY,availW*.63,availH*.62)
        }
        drawRebarDetailPanel(w,pkg,baseX+availW*.66,baseY+availH*.40,availW*.31,availH*.45)
        drawBbsMini(w,bbs,baseX+availW*.66,baseY+availH*.03,availW*.31,availH*.33)
        w.raw("0\nENDSEC\n"); objectsOnly(w); w.raw("0\nEOF\n"); writeDxfFile(w, file)
    }

    private fun beam(w:W,p:CalculatorDetailingV4.DetailingPackage,x:Double,y:Double,tw:Double,th:Double){val l=p.geometry["span"]?:4000.0;val d=p.geometry["depth"]?:500.0;val sc=min(tw/max(l,1.0),th/max(d,1.0))*0.9;val ox=x;val oy=y+60;w.text(ox,oy+d*sc+18,"ELEVATION",3.6);w.rect(ox,oy,l*sc,d*sc,"CONCRETE",7,35);drawBarsInMember(w,p,ox,oy,sc,d,l,true);w.dim(ox,oy,ox+l*sc,oy,-18.0,"L=${fmt(l)}");section(w,p,ox+tw*.75,oy+th*.52,90.0,80.0);}
    private fun column(w:W,p:CalculatorDetailingV4.DetailingPackage,x:Double,y:Double,tw:Double,th:Double){val b=p.geometry["width"]?:400.0;val d=p.geometry["depth"]?:b;val h=p.geometry["height"]?:3000.0;val sc=min(tw/max(b,1.0),th/max(h,1.0))*0.85;w.text(x,y+h*sc+18,"ELEVATION",3.6);w.rect(x,y,b*sc,h*sc,"CONCRETE",7,35);p.bars.forEachIndexed{idx,bar->val xx=x+(30.0+idx*max(20.0,(b-60.0)/max(1,p.bars.size-1)))*sc;w.line(xx,y,xx,y+h*sc,"REBAR",1,20)};drawColumnTies(w,p,x,y,sc,b,h);w.dim(x,y,x+b*sc,y,-18.0,"b=${fmt(b)}");section(w,p,x+tw*.74,y+th*.50,85.0,85.0)}
    private fun slab(w:W,p:CalculatorDetailingV4.DetailingPackage,x:Double,y:Double,tw:Double,th:Double){val lx=p.geometry["lx"]?:5000.0;val ly=p.geometry["ly"]?:5000.0;val sc=min(tw/max(lx,1.0),th/max(ly,1.0))*0.82;w.text(x,y+ly*sc+18,"PLAN",3.6);w.rect(x,y,lx*sc,ly*sc,"CONCRETE",7,30);p.bars.forEach{b->val sp=b.spacingMm?:return@forEach;var q=0.0;val horiz=b.layer!="REBAR_SEC";while(q<=if(horiz)ly else lx){if(horiz)w.line(x,y+q*sc,x+lx*sc,y+q*sc,b.layer,if(b.layer=="REBAR_TOP")5 else 1,18) else w.line(x+q*sc,y,x+q*sc,y+ly*sc,b.layer,4,18);q+=sp}};section(w,p,x+tw*.74,y+th*.52,120.0,80.0)}
    private fun footing(w:W,p:CalculatorDetailingV4.DetailingPackage,x:Double,y:Double,tw:Double,th:Double){val l=p.geometry["length"]?:2500.0;val b=p.geometry["width"]?:2500.0;val c=p.geometry["cover"]?:75.0;val sc=min(tw/l,th/b)*0.82;w.text(x,y+b*sc+18,"PLAN",3.6);w.rect(x,y,l*sc,b*sc,"CONCRETE",7,35);val cl=p.geometry["columnLength"]?:300.0;val cw=p.geometry["columnWidth"]?:300.0;w.rect(x+(l-cl)/2*sc,y+(b-cw)/2*sc,cl*sc,cw*sc,"CONCRETE",4,25);p.bars.forEach{bd->val sp=bd.spacingMm?:return@forEach;var q=c;while(q<=b-c){w.line(x+c*sc,y+q*sc,x+(l-c)*sc,y+q*sc,bd.layer,1,18);q+=sp}};section(w,p,x+tw*.74,y+th*.52,125.0,80.0)}
    private fun wall(w:W,p:CalculatorDetailingV4.DetailingPackage,x:Double,y:Double,tw:Double,th:Double){val h=p.geometry["height"]?:3000.0;val bt=p.geometry["baseThickness"]?:400.0;val bw=p.geometry["baseWidth"]?:2200.0;val toe=p.geometry["toe"]?:500.0;val stem=p.geometry["stemBottom"]?:300.0;val sc=min(tw/bw,th/(h+bt))*0.8;w.text(x,y+(h+bt)*sc+18,"SECTION",3.6);w.poly(listOf(x to y,(x+bw*sc) to y,(x+bw*sc) to y+bt*sc,(x+(toe+stem)*sc) to y+(bt+h)*sc,(x+toe*sc) to y+(bt+h)*sc,(x+toe*sc) to y+bt*sc),"CONCRETE",true,7,30);drawSoil(w,x+bw*sc+18,y+bt*sc,h*sc);drawBarsInMember(w,p,x,y,sc,h+bt,bw,false);}
    private fun tank(w:W,p:CalculatorDetailingV4.DetailingPackage,x:Double,y:Double,tw:Double,th:Double){val l=p.geometry["length"]?:5000.0;val h=p.geometry["height"]?:3000.0;val wt=p.geometry["wallThickness"]?:250.0;val bt=p.geometry["baseThickness"]?:300.0;val wl=p.geometry["waterLevel"]?:h;val sc=min(tw/(l+2*wt),th/(h+bt))*0.8;w.text(x,y+(h+bt)*sc+18,"SECTION",3.6);w.rect(x,y,(l+2*wt)*sc,bt*sc,"CONCRETE",7,30);w.rect(x,y+bt*sc,wt*sc,h*sc,"CONCRETE",7,30);w.rect(x+(l+wt)*sc,y+bt*sc,wt*sc,h*sc,"CONCRETE",7,30);w.line(x+wt*sc,y+(bt+wl)*sc,x+(wt+l)*sc,y+(bt+wl)*sc,"WATER",5,18);drawWater(w,x+(l+2*wt)*sc+18,y+bt*sc,h*sc);drawBarsInMember(w,p,x,y,sc,h+bt,l,false)}
    private fun stair(w:W,p:CalculatorDetailingV4.DetailingPackage,x:Double,y:Double,tw:Double,th:Double){val sp=p.geometry["span"]?:3500.0;val r=p.geometry["riser"]?:160.0;val t=p.geometry["tread"]?:280.0;val steps=max(1,floor(sp/max(t,1.0)).toInt());val tr=steps*r;val sc=min(tw/sp,th/max(tr,1.0))*0.82;w.text(x,y+tr*sc+18,"SECTION",3.6);var xx=0.0;var yy=0.0;repeat(steps){w.line(x+xx*sc,y+yy*sc,x+xx*sc,y+(yy+r)*sc,"CONCRETE",7,20);w.line(x+xx*sc,y+(yy+r)*sc,x+(xx+t)*sc,y+(yy+r)*sc,"CONCRETE",7,20);xx+=t;yy+=r};w.line(x,y,x+xx*sc,y+yy*sc,"CONCRETE",7,20);drawBarsInMember(w,p,x,y,sc,max(tr,1.0),sp,false)}
    private fun steel(w:W,p:CalculatorDetailingV4.DetailingPackage,x:Double,y:Double,tw:Double,th:Double){val l=p.geometry["span"]?:5000.0;val d=p.geometry["depth"]?:400.0;val bf=p.geometry["flangeWidth"]?:180.0;val tf=p.geometry["flangeThickness"]?:12.0;val web=p.geometry["webThickness"]?:8.0;val sc=min(tw/l,th/d)*0.82;w.text(x,y+d*sc+18,"ELEVATION",3.6);w.rect(x,y,l*sc,d*sc,"STEEL",7,35);w.rect(x,y,l*sc,tf*sc,"STEEL",7,35);w.rect(x,y+(d-tf)*sc,l*sc,tf*sc,"STEEL",7,35);sectionSteel(w,x+tw*.76,y+th*.52,bf,tf,web,d)}
    private fun connection(w:W,p:CalculatorDetailingV4.DetailingPackage,x:Double,y:Double,tw:Double,th:Double){val cw=p.geometry["columnWidth"]?:500.0;val cd=p.geometry["columnDepth"]?:500.0;val bd=p.geometry["beamDepth"]?:450.0;val bw=p.geometry["beamWidth"]?:250.0;val pt=p.geometry["plateThickness"]?:20.0;val bolt=p.geometry["boltDiameter"]?:22.0;val rows=max(1,(p.geometry["boltRows"]?:4.0).toInt());val sc=min(tw/(cw+bw),th/cd)*0.75;w.text(x,y+cd*sc+18,"MOMENT CONNECTION",3.6);w.rect(x,y,cw*sc,cd*sc,"STEEL",7,35);val by=y+(cd/2-bd/2)*sc;w.rect(x+cw*sc,by,bw*sc,bd*sc,"STEEL",7,35);w.rect(x+(cw-pt)*sc,by,pt*sc,bd*sc,"WELDS",2,25);for(i in 0 until rows){val yy=by+50*sc+i*((bd-100)*sc/max(1,rows-1));w.circle(x+(cw-pt/2)*sc,yy,bolt*sc/2,"BOLTS",4,18)}}

    private fun drawBarsInMember(w:W,p:CalculatorDetailingV4.DetailingPackage,x:Double,y:Double,sc:Double,h:Double,length:Double,beam:Boolean){p.bars.take(30).forEachIndexed{idx,b->val baseY=y+if(beam)25.0*sc else 40.0*sc;drawBarShape(w,b,x+idx*20.0,baseY,sc)}}
    private fun drawColumnTies(w:W,p:CalculatorDetailingV4.DetailingPackage,x:Double,y:Double,sc:Double,b:Double,h:Double){p.stirrups.forEach{st->val sp=st.spacingMm?:return@forEach;var yy=0.0;while(yy<=h){w.rect(x+18*sc,y+yy*sc,max(20.0,(b-36)*sc),1.5,"STIRRUPS",2,12);yy+=sp}}}

    /** Real rebar shape geometry. Coordinates are local to the detail panel. */
    private fun drawBarShape(w:W,b:CalculatorDetailingV4.BarDefinition,x:Double,y:Double,sc:Double){
        w.marks+=b.mark
        val d=b.diameterMm*sc; val s=b.segments
        when(b.shape){
            CalculatorDetailingV4.BarShape.STRAIGHT->{val len=(b.straightLengthMm?:s.sumOf{it.length}).coerceAtLeast(20.0)*sc;w.line(x,y,x+len,y,b.layer,1,18)}
            CalculatorDetailingV4.BarShape.L->{val a=(b.straightLengthMm?:500.0)*sc;w.line(x,y,x+a,y,b.layer,1,18);w.line(x+a,y,x+a,y+a,b.layer,1,18);w.text(x,y+5.0,"${b.mark} L",4.0,b.layer)}
            CalculatorDetailingV4.BarShape.U->{val a=(b.straightLengthMm?:500.0)*sc;w.line(x,y,x+a,y,b.layer,1,18);w.line(x,y,x,y+a,b.layer,1,18);w.line(x+a,y,x+a,y+a,b.layer,1,18);w.text(x,y+5.0,"${b.mark} U",4.0,b.layer)}
            CalculatorDetailingV4.BarShape.C->{val a=(b.straightLengthMm?:500.0)*sc;w.line(x,y,x+a,y,b.layer,1,18);w.line(x,y,x,y+a,b.layer,1,18);w.line(x+a,y,x+a,y-a*.65,b.layer,1,18);w.text(x,y+5.0,"${b.mark} C",4.0,b.layer)}
            CalculatorDetailingV4.BarShape.STIRRUP_90,CalculatorDetailingV4.BarShape.STIRRUP_135,CalculatorDetailingV4.BarShape.CROSSTIE_135->{val a=(b.straightLengthMm?:400.0)*sc;val b2=(a*.65).coerceAtLeast(d*4);w.rect(x,y,b2,a*.7,b.layer,1,18);val hook=if(b.shape==CalculatorDetailingV4.BarShape.STIRRUP_135||b.shape==CalculatorDetailingV4.BarShape.CROSSTIE_135) 135 else 90;w.text(x,y+a*.82,"${b.mark} ${hook}°",4.0,b.layer)}
            CalculatorDetailingV4.BarShape.HOOP->{val r=max(20.0,(b.straightLengthMm?:250.0)/PI/2)*sc;w.circle(x+r,y+r,r,b.layer,1,18);w.text(x,y+2*r+6.0,"${b.mark} HOOP",4.0,b.layer)}
            CalculatorDetailingV4.BarShape.CUSTOM->{if(s.isNotEmpty()){var px=x;var py=y;s.forEach{seg->val a=Math.toRadians(seg.angleDeg);val nx=px+seg.length*sc*cos(a);val ny=py+seg.length*sc*sin(a);w.line(px,py,nx,ny,b.layer,1,18);px=nx;py=ny}}}
        }
        var yy=y-7;w.text(x,yy,"${b.mark} Ø${fmt(b.diameterMm.toDouble())} CUT ${fmt(cutLength(b))} mm",4.2,"BBS")
        b.lapLocations.forEach{lp->val xx=x+lp.positionFromStartMm*sc;w.line(xx,y-6,xx,y+6,"LAP",1,13);w.text(xx+3.0,y+8.0,"LAP ${fmt(lp.lengthMm)}",3.0,"LAP",1)}
        if((b.anchorageLengthMm?:0.0)>0)w.text(x,y-14,"Ld ${fmt(b.anchorageLengthMm!!)}",3.2,"REBAR")
    }
    private fun cutLength(b:CalculatorDetailingV4.BarDefinition): Double {
        var x = b.straightLengthMm ?: b.segments.sumOf { it.length }
        x += b.hookStartLengthMm ?: 0.0
        x += b.hookEndLengthMm ?: 0.0
        x += b.bendAllowanceMm
        x += b.anchorageLengthMm ?: 0.0
        x += b.lapLengthMm ?: 0.0
        x -= (b.cutOffFromStartMm ?: 0.0)
        x -= (b.cutOffFromEndMm ?: 0.0)
        return kotlin.math.max(0.0, x)
    }

    private fun section(w:W,p:CalculatorDetailingV4.DetailingPackage,x:Double,y:Double,ww:Double,hh:Double){w.text(x,y+hh+10,"SECTION",3.3);w.rect(x,y,ww,hh,"CONCRETE",7,25);val c=p.geometry["cover"]?:30.0;if(ww>2*c*.2&&hh>2*c*.2){w.rect(x+ww*.12,y+hh*.12,ww*.76,hh*.76,"STIRRUPS",2,13)};p.bars.take(8).forEachIndexed{i,b->val bx=x+ww*(.16+i*.68/max(1,p.bars.take(8).size-1));w.circle(bx,y+hh*.18,max(2.0,b.diameterMm.toDouble()/15.0),"REBAR",1,13)}}
    private fun sectionSteel(w:W,x:Double,y:Double,bf:Double,tf:Double,tw:Double,d:Double){val sc=min(80.0/max(bf,1.0),60.0/max(d,1.0));w.rect(x-bf*sc/2,y,bf*sc,tf*sc,"STEEL",7,25);w.rect(x-tw*sc/2,y+tf*sc,tw*sc,(d-2*tf)*sc,"STEEL",7,25);w.rect(x-bf*sc/2,y+(d-tf)*sc,bf*sc,tf*sc,"STEEL",7,25)}
    private fun drawSoil(w:W,x:Double,y:Double,h:Double){w.line(x,y,x,y+h,"LOADS",6,13);w.poly(listOf(x to y+h,(x+45) to y,x to y),"LOADS",true,6,13);w.text(x+50,y+h*.5,"Pa",3.0,"LOADS",6)}
    private fun drawWater(w:W,x:Double,y:Double,h:Double){w.line(x,y,x,y+h,"LOADS",5,13);w.poly(listOf(x to y+h,(x+45) to y,x to y),"LOADS",true,5,13);w.text(x+50,y+h*.5,"Pw",3.0,"LOADS",5)}

    private fun drawRebarDetailPanel(w:W,p:CalculatorDetailingV4.DetailingPackage,x:Double,y:Double,tw:Double,th:Double){w.rect(x,y,tw,th,"PANEL",7,13);w.text(x+5,y+th-10,"BAR SHAPES / CUTTING DETAILS",4.2,"TEXT");var yy=y+th-23;p.bars.take(12).forEach{b->drawBarShape(w,b,x+8,yy,0.08);yy-=18};p.stirrups.take(5).forEach{b->drawBarShape(w,b,x+8,yy,0.08);yy-=18}}
    private fun drawBbsMini(w:W,bbs:CalculatorDetailingV4.BarSchedule,x:Double,y:Double,tw:Double,th:Double){w.rect(x,y,tw,th,"PANEL",7,13);w.text(x+5,y+th-10,"BBS",4.2,"BBS");var yy=y+th-22;w.text(x+5,yy,"MARK",2.7,"BBS");w.text(x+42,yy,"D",2.7,"BBS");w.text(x+58,yy,"QTY",2.7,"BBS");w.text(x+82,yy,"CUT",2.7,"BBS");w.text(x+125,yy,"WT",2.7,"BBS");yy-=9;bbs.rows.take(12).forEach{r->w.text(x+5,yy,r.mark,2.7,"BBS");w.text(x+42,yy,fmt(r.diameterMm.toDouble()),2.7,"BBS");w.text(x+58,yy,r.quantity.toString(),2.7,"BBS");w.text(x+82,yy,fmt(r.individualLengthMm),2.7,"BBS");w.text(x+125,yy,fmt(r.totalWeightKg),2.7,"BBS");yy-=8};w.text(x+5,y+8,"TOTAL ${fmt(bbs.totalWeightKg)} kg",3.0,"BBS")}

    private fun frame(w:W,s:Settings,no:String,p:CalculatorDetailingV4.DetailingPackage){val pw=s.paper.width;val ph=s.paper.height;w.rect(0.0,0.0,pw,ph,"BORDER",7,50);w.rect(7.0,7.0,pw-14,ph-14,"BORDER",7,25);w.text(12.0,ph-15,s.projectName,5.0,"TEXT");w.text(12.0,ph-27,"${no} | ${p.title} | ${p.memberId}",3.8,"TEXT");w.text(12.0,12.0,"REV ${s.revision} | SCALE ${s.scaleLabel} | CODE ${s.code}",3.0,"NOTES");w.rect(pw-170.0,7.0,163.0,34.0,"REVISION",7,25);w.text(pw-164.0,32.0,"DWG ${no}",3.0,"REVISION");w.text(pw-164.0,22.0,"REV ${s.revision}",3.0,"REVISION");w.text(pw-164.0,12.0,"PREP ${s.preparedBy}",2.5,"REVISION")}

    private fun writeBbsSheet(dir:File,bbs:CalculatorDetailingV4.BarSchedule,s:Settings):File{val f=File(dir,"S-98_MASTER_BBS.dxf");val w=W(s);header(w);classes(w);tables(w);blocksOnly(w);w.raw("0\nSECTION\n2\nENTITIES\n");frameRaw(w,s,"S-98","MASTER BAR BENDING SCHEDULE");val x=15.0;var y=s.paper.height-55;w.text(x,y,"MASTER BAR BENDING SCHEDULE",5.5,"BBS");y-=14;val cols=listOf("MARK" to 18.0,"MEMBER" to 45.0,"D" to 18.0,"SHAPE" to 48.0,"QTY" to 18.0,"CUT" to 36.0,"TOTAL m" to 30.0,"KG" to 30.0);var xx=x;cols.forEach{w.text(xx,y,it.first,2.8,"BBS");xx+=it.second};y-=8;bbs.rows.take(22).forEach{r->xx=x;val vals=listOf(r.mark,r.memberId,fmt(r.diameterMm.toDouble()),r.shape.name,r.quantity.toString(),fmt(r.individualLengthMm),fmt(r.totalLengthM),fmt(r.totalWeightKg));vals.forEachIndexed{i,v->w.text(xx,y,v,2.6,"BBS");xx+=cols[i].second};y-=7};w.text(x,12.0,"TOTAL STEEL = "+fmt(bbs.totalWeightKg)+" kg | GENERATED "+bbs.generatedAt,3.2,"NOTES");w.raw("0\nENDSEC\n");objectsOnly(w);w.raw("0\nEOF\n");writeDxfFile(w,f);return f}
    private fun frameRaw(w:W,s:Settings,no:String,title:String){w.rect(0.0,0.0,s.paper.width,s.paper.height,"BORDER",7,50);w.text(12.0,s.paper.height-15,s.projectName,5.0,"TEXT");w.text(12.0,s.paper.height-27,"$no | $title | REV ${s.revision}",3.8,"TEXT")}

    private fun writeIndex(dir:File,sheets:List<Sheet>,s:Settings,bbs:File?):File{val f=File(dir,"S-00_DRAWING_INDEX.dxf");val w=W(s);header(w);classes(w);tables(w);blocksOnly(w);w.raw("0\nSECTION\n2\nENTITIES\n");frameRaw(w,s,"S-00","DRAWING INDEX");w.text(15.0,s.paper.height-55,"DRAWING INDEX",5.5,"TEXT");var y=s.paper.height-72;w.text(15.0,y,"NO.",3.0,"TEXT");w.text(45.0,y,"TITLE",3.0,"TEXT");w.text(165.0,y,"MEMBER",3.0,"TEXT");w.text(250.0,y,"FILE",3.0,"TEXT");y-=10;sheets.forEach{w.text(15.0,y,it.number,3.0);w.text(45.0,y,it.title,3.0);w.text(165.0,y,it.memberId,3.0);w.text(250.0,y,it.file.name,2.8);y-=8};bbs?.let{w.text(15.0,25.0,"BBS SHEET: "+it.name,3.0,"NOTES")};w.raw("0\nENDSEC\n");objectsOnly(w);w.raw("0\nEOF\n");writeDxfFile(w,f);return f}


    private fun writeQa(dir:File,qa:Qa,s:Settings):File{
        val f=File(dir,"PROJECT_CAD_QA.txt").apply{parentFile?.mkdirs()}
        val lines=buildList{
            add("CIVILEG CAD ENGINE V7 QA")
            add("PASS=${qa.passed}")
            add("GENERATED=${qa.generatedAt}")
            add("PROJECT=${s.projectName}")
            add("REVISION=${s.revision}")
            add("SHEETS=${qa.sheets}")
            add("BBS_ROWS=${qa.bars}")
            add("TOTAL_STEEL_KG=${fmt(qa.steelKg)}")
            add("-- ISSUES --")
            if(qa.issues.isEmpty()) add("NONE") else addAll(qa.issues)
        }
        f.writeText(lines.joinToString("\n")+"\n", StandardCharsets.UTF_8)
        return f
    }

    private fun validate(packages:List<CalculatorDetailingV4.DetailingPackage>,bbs:CalculatorDetailingV4.BarSchedule,issues:MutableList<String>){val ids=packages.map{it.memberId};ids.groupingBy{it}.eachCount().filterValues{it>1}.forEach{(k,v)->issues+="Duplicate Member ID $k x$v"};if(bbs.rows.isEmpty()&&packages.any{it.bars.isNotEmpty()||it.stirrups.isNotEmpty()})issues+="Reinforcement exists but BBS is empty";bbs.rows.forEach{if(it.quantity<=0)issues+="${it.mark}: invalid quantity";if(it.individualLengthMm<=0)issues+="${it.mark}: invalid cutting length"}}
    private fun qaScan(file:File):List<String>{if(!file.exists())return listOf("Missing CAD file ${file.name}");val t=file.readText();val out=mutableListOf<String>();if(!t.contains("0\nEOF\n"))out+="${file.name}: missing DXF terminator";if(!t.contains("AcDbEntity"))out+="${file.name}: missing DXF entity subclass markers";if(t.count{it=='\n'}<200)out+="${file.name}: suspiciously small drawing";return out}

    private fun title(t:CalculatorDetailingV4.MemberType)=when(t){CalculatorDetailingV4.MemberType.BEAM->"RC_BEAM";CalculatorDetailingV4.MemberType.COLUMN->"RC_COLUMN";CalculatorDetailingV4.MemberType.SLAB->"RC_SLAB";CalculatorDetailingV4.MemberType.FOOTING->"FOUNDATION";CalculatorDetailingV4.MemberType.WALL->"WALL";CalculatorDetailingV4.MemberType.TANK->"TANK";CalculatorDetailingV4.MemberType.STAIR->"STAIR";CalculatorDetailingV4.MemberType.STEEL_MEMBER->"STEEL";CalculatorDetailingV4.MemberType.CONNECTION->"CONNECTION"}
    private fun safe(s:String)=s.replace(Regex("[^A-Za-z0-9_-]+"),"_")
    private fun fmt(v:Double)=String.format(Locale.US,"%.2f",v)
    private fun header(w: W) {
        val d = "$"
        w.raw("0\nSECTION\n2\nHEADER\n")
        w.raw("9\n${d}ACADVER\n1\nAC1027\n")
        w.raw("9\n${d}DWGCODEPAGE\n3\nutf_8\n")
        w.raw("9\n${d}INSBASE\n10\n0.0\n20\n0.0\n30\n0.0\n")
        w.raw("9\n${d}EXTMIN\n10\n__EXTMINX__\n20\n__EXTMINY__\n30\n0.0\n")
        w.raw("9\n${d}EXTMAX\n10\n__EXTMAXX__\n20\n__EXTMAXY__\n30\n0.0\n")
        w.raw("9\n${d}INSUNITS\n70\n4\n")
        w.raw("9\n${d}MEASUREMENT\n70\n1\n")
        w.raw("9\n${d}DIMSTYLE\n2\nStandard\n")
        w.raw("9\n${d}CELTYPE\n6\nBYLAYER\n")
        w.raw("9\n${d}TEXTSTYLE\n7\nSTANDARD\n")
        w.raw("9\n${d}CLAYER\n7\nTEXT\n")
        w.raw("9\n${d}HANDSEED\n5\n__HANDSEED__\n")
        w.raw("0\nENDSEC\n")
    }
    private fun tables(w:W){val defs=listOf("BORDER" to 7,"CONCRETE" to 8,"REBAR" to 1,"REBAR_TOP" to 5,"STIRRUPS" to 2,"STEEL" to 7,"BOLTS" to 4,"WELDS" to 2,"LOADS" to 6,"DIM" to 5,"TEXT" to 7,"NOTES" to 3,"BBS" to 7,"LAP" to 1,"PANEL" to 8,"WATER" to 5,"REVISION" to 1);w.raw("0\nSECTION\n2\nTABLES\n0\nTABLE\n2\nLTYPE\n5\n${w.h()}\n100\nAcDbSymbolTable\n70\n1\n0\nLTYPE\n5\n${w.h()}\n100\nAcDbSymbolTableRecord\n100\nAcDbLinetypeTableRecord\n2\nCONTINUOUS\n70\n0\n3\nSolid line\n72\n65\n73\n0\n40\n0.0\n0\nENDTAB\n0\nTABLE\n2\nLAYER\n5\n${w.h()}\n100\nAcDbSymbolTable\n70\n${defs.size}\n");defs.forEach{(n,c)->w.raw("0\nLAYER\n5\n${w.h()}\n100\nAcDbSymbolTableRecord\n100\nAcDbLayerTableRecord\n2\n$n\n70\n0\n62\n$c\n6\nCONTINUOUS\n")};w.raw("0\nENDTAB\n0\nTABLE\n2\nSTYLE\n5\n${w.h()}\n100\nAcDbSymbolTable\n70\n1\n0\nSTYLE\n5\n${w.h()}\n100\nAcDbSymbolTableRecord\n100\nAcDbTextStyleTableRecord\n2\nSTANDARD\n70\n0\n40\n0.0\n41\n1.0\n50\n0.0\n71\n0\n42\n2.5\n3\ntxt\n4\n\n0\nENDTAB\n0\nTABLE\n2\nDIMSTYLE\n5\n${w.h()}\n100\nAcDbSymbolTable\n70\n1\n0\nDIMSTYLE\n105\n${w.h()}\n100\nAcDbSymbolTableRecord\n100\nAcDbDimStyleTableRecord\n2\nStandard\n70\n0\n3\n\n4\n\n40\n1.0\n41\n3.0\n42\n0.0\n43\n0.0\n44\n1.0\n45\n0.0\n46\n0.0\n47\n0.0\n48\n0.0\n140\n1.0\n141\n2.5\n142\n0.0\n143\n25.4\n144\n1.0\n145\n0.0\n146\n1.0\n147\n50.0\n148\n0.0\n71\n0\n72\n0\n73\n0\n74\n0\n75\n0\n76\n0\n77\n1\n78\n0\n79\n0\n170\n0\n0\nENDTAB\n0\nTABLE\n2\nVPORT)}\n100\nAcDbSymbolTable\n70\n0\n0\nENDTAB\n0\nTABLE\n2\nVIEW\n5\n${w.h()}\n100\nAcDbSymbolTable\n70\n0\n0\nENDTAB\n0\nTABLE\n2\nUCS\n5\n${w.h()}\n100\nAcDbSymbolTable\n70\n0\n0\nENDTAB\n0\nTABLE\n2\nAPPID\n5\n${w.h()}\n100\nAcDbSymbolTable\n70\n1\n0\nAPPID\n5\n${w.h()}\n100\nAcDbSymbolTableRecord\n100\nAcDbRegAppTableRecord\n2\nACAD\n70\n0\n0\nENDTAB\n0\nENDSEC\n")}
    private fun classes(w:W){w.raw("0\nSECTION\n2\nCLASSES\n0\nENDSEC\n")}
    private fun blocksOnly(w:W){w.raw("0\nSECTION\n2\nBLOCKS\n0\nBLOCK\n5\n"+w.h()+"\n100\nAcDbEntity\n8\n0\n100\nAcDbBlockBegin\n2\n*MODEL_SPACE\n70\n0\n10\n0.0\n20\n0.0\n30\n0.0\n3\n*MODEL_SPACE\n1\n\n0\nENDBLK\n5\n"+w.h()+"\n100\nAcDbEntity\n8\n0\n100\nAcDbBlockEnd\n0\nBLOCK\n5\n"+w.h()+"\n100\nAcDbEntity\n67\n1\n8\n0\n100\nAcDbBlockBegin\n2\n*PAPER_SPACE\n70\n0\n10\n0.0\n20\n0.0\n30\n0.0\n3\n*PAPER_SPACE\n1\n\n0\nENDBLK\n5\n"+w.h()+"\n100\nAcDbEntity\n67\n1\n8\n0\n100\nAcDbBlockEnd\n0\nENDSEC\n")}
    private fun objectsOnly(w:W){
        val acadGroupH = w.h()
        val rootH = w.h()
        w.raw("0\nSECTION\n2\nOBJECTS\n")
        w.raw("0\nDICTIONARY\n5\n$rootH\n100\nAcDbDictionary\n281\n1\n3\nACAD_GROUP\n350\n$acadGroupH\n")
        w.raw("0\nDICTIONARY\n5\n$acadGroupH\n100\nAcDbDictionary\n281\n0\n")
        w.raw("0\nENDSEC\n")
    }
    /** Write DXF content to file with CRLF and HANDSEED replacement. */
    private fun writeDxfFile(w: W, file: File) {
        val handSeed = w.handle.toString(16).uppercase(Locale.US)
        val bx = if (w.box[0] == Double.POSITIVE_INFINITY) 0.0 else w.box[0]
        val by = if (w.box[1] == Double.POSITIVE_INFINITY) 0.0 else w.box[1]
        val tx = if (w.box[2] == Double.NEGATIVE_INFINITY) 100.0 else w.box[2]
        val ty = if (w.box[3] == Double.NEGATIVE_INFINITY) 100.0 else w.box[3]
        val pad = 10.0
        val content = w.sb.toString()
            .replace("__HANDSEED__", handSeed)
            .replace("__EXTMINX__", fmt(bx - pad))
            .replace("__EXTMINY__", fmt(by - pad))
            .replace("__EXTMAXX__", fmt(tx + pad))
            .replace("__EXTMAXY__", fmt(ty + pad))
            .replace("\n", "\r\n")
        file.parentFile?.mkdirs()
        FileOutputStream(file).use { it.write(content.toByteArray(StandardCharsets.UTF_8)) }
    }

    @Deprecated("Use blocksOnly + objectsOnly separately")
    private fun blocksAndObjects(w:W){w.raw("0\nSECTION\n2\nBLOCKS\n0\nBLOCK\n5\n${w.h()}\n100\nAcDbEntity\n8\n0\n100\nAcDbBlockBegin\n2\n*MODEL_SPACE\n70\n0\n10\n0.0\n20\n0.0\n30\n0.0\n3\n*MODEL_SPACE\n1\n\n0\nENDBLK\n5\n${w.h()}\n100\nAcDbEntity\n8\n0\n100\nAcDbBlockEnd\n0\nBLOCK\n5\n${w.h()}\n100\nAcDbEntity\n67\n1\n8\n0\n100\nAcDbBlockBegin\n2\n*PAPER_SPACE\n70\n0\n10\n0.0\n20\n0.0\n30\n0.0\n3\n*PAPER_SPACE\n1\n\n0\nENDBLK\n5\n${w.h()}\n100\nAcDbEntity\n67\n1\n8\n0\n100\nAcDbBlockEnd\n0\nENDSEC\n");objectsOnly(w)}
}
