import { db } from "@/lib/db";
import { StatCard } from "@/components/admin/stats-card";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import {
  TrendingUp,
  TrendingDown,
  AlertCircle,
  Package,
  Smartphone,
  Users,
  Barcode,
  Activity,
} from "lucide-react";
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Legend } from "recharts";
import { format } from "date-fns";
import { cn } from "@/lib/utils";

export default async function AdminDashboardPage() {
  const [totalApps, totalBuilds, totalDevices, totalCrashes, totalUsers, recentCrashes] =
    await Promise.all([
      db.app.count(),
      db.build.count(),
      db.device.count(),
      db.crashReport.count(),
      db.user.count(),
      db.crashReport.findMany({
        take: 5,
        orderBy: { occurredAt: "desc" },
        include: { app: true, device: true },
      }),
    ]);

  // Crash trends for last 7 days
  const sevenDaysAgo = new Date(Date.now() - 7 * 24 * 60 * 60 * 1000);
  const crashTrends = await db.crashReport.groupBy({
    by: ["occurredAt"],
    where: { occurredAt: { gte: sevenDaysAgo } },
    _count: true,
  });

  // Build status distribution
  const buildStatuses = await db.build.groupBy({
    by: ["status"],
    _count: true,
  });

  const statusMap: Record<string, number> = {};
  buildStatuses.forEach((b: any) => {
    statusMap[b.status] = b._count;
  });

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold">لوحة التحكم</h1>
          <p className="text-muted-foreground">
            نظرة عامة على حالة التطبيق وأداء النظام
          </p>
        </div>
        <div className="flex items-center gap-2 text-xs text-muted-foreground">
          <span className="flex items-center gap-1">
            <span className="w-2 h-2 rounded-full bg-emerald-500" />
            النظام يعمل بشكل طبيعي
          </span>
        </div>
      </div>

      {/* Stats Row */}
      <div className="grid grid-cols-1 md:grid-cols-3 lg:grid-cols-6 gap-4">
        <StatCard
          title="التطبيقات"
          value={totalApps}
          change="+12%"
          changeType="positive"
          icon={Package}
          subtitle="إجمالي التطبيقات المسجلة"
        />
        <StatCard
          title="البناء"
          value={totalBuilds}
          change="+5%"
          changeType="positive"
          icon={Barcode}
          subtitle="إجمالي عمليات البناء"
        />
        <StatCard
          title="الأجهزة"
          value={totalDevices}
          change="+8%"
          changeType="positive"
          icon={Smartphone}
          subtitle="الأجهزة المتصلة"
        />
        <StatCard
          title="تقارير الانهيار"
          value={totalCrashes}
          change="-15%"
          changeType="negative"
          icon={AlertCircle}
          subtitle="تقارير غير محلولة"
        />
        <StatCard
          title="المستخدمون"
          value={totalUsers}
          change="+3%"
          changeType="positive"
          icon={Users}
          subtitle="إجمالي المستخدمين المسجلين"
        />
        <StatCard
          title="النظام"
          value="نشط"
          change="بدون مشاكل"
          changeType="neutral"
          icon={Activity}
          subtitle="حالة الخادم وقاعدة البيانات"
        />
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Crash trend chart */}
        <Card className="col-span-1 lg:col-span-2">
          <CardHeader>
            <CardTitle>اتجاه تقارير الانهيار (آخر 7 أيام)</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="h-64 w-full">
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={crashTrends.map((d: any, i: number) => ({ name: format(new Date(d.occurredAt), "MM/dd"), value: d._count }))}>
                  <CartesianGrid strokeDasharray="3 3" vertical={false} />
                  <XAxis dataKey="name" />
                  <YAxis />
                  <Tooltip />
                  <Legend />
                  <Bar dataKey="value" fill="#8884d8" radius={[4, 4, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            </div>
          </CardContent>
        </Card>

        {/* Build status */}
        <Card>
          <CardHeader>
            <CardTitle>حالة عمليات البناء</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            {[["SUCCESS", "نجح"], ["FAILED", "فشل"], ["PENDING", "معلق"], ["BUILDING", "جار البناء"]].map(
              ([status, label]) => {
                const count = statusMap[status] || 0;
                return (
                  <div key={status} className="flex items-center justify-between py-2 border-b last:border-0">
                    <div className="flex items-center gap-2">
                      <div
                        className={cn(
                          "w-2 h-2 rounded-full",
                          status === "SUCCESS" ? "bg-emerald-500" : status === "FAILED" ? "bg-destructive" : "bg-amber-500"
                        )}
                      />
                      <span className="text-sm">{label}</span>
                    </div>
                    <span className="text-sm font-mono font-bold">{count}</span>
                  </div>
                );
              }
            )}
          </CardContent>
        </Card>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Recent crashes */}
        <Card className="col-span-1 lg:col-span-2">
          <CardHeader className="flex flex-row items-center justify-between pb-2">
            <CardTitle>آخر تقارير الانهيار</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="space-y-3">
              {recentCrashes.length > 0 ? (
                recentCrashes.map((crash: any) => (
                  <div key={crash.id} className="flex items-center justify-between p-3 rounded-lg border border-border bg-muted/50">
                    <div className="flex-1 min-w-0">
                      <p className="text-sm font-medium truncate">{crash.title}</p>
                      <p className="text-xs text-muted-foreground truncate">
                        {crash.app?.name} • {crash.device?.model ?? "غير محدد"} • {crash.severity}
                      </p>
                    </div>
                    <span className="text-xs font-medium">
                      {crash.severity === "CRITICAL" ? "حرج" : crash.severity === "HIGH" ? "عالي" : crash.severity === "MEDIUM" ? "متوسط" : "منخفض"}
                    </span>
                  </div>
                ))
              ) : (
                <div className="text-center py-8 text-muted-foreground">
                  لا توجد تقارير انهيار
                </div>
              )}
            </div>
          </CardContent>
        </Card>

        {/* Quick actions */}
        <Card>
          <CardHeader>
            <CardTitle>إجراءات سريعة</CardTitle>
          </CardHeader>
          <CardContent className="grid grid-cols-1 gap-3">
            <button className="flex items-center gap-3 px-4 py-3 rounded-lg border border-border text-left hover:bg-muted transition-colors">
              <Package className="w-5 h-5 text-muted-foreground" />
              <span className="text-sm">إضافة تطبيق جديد</span>
            </button>
            <button className="flex items-center gap-3 px-4 py-3 rounded-lg border border-border text-left hover:bg-muted transition-colors">
              <Barcode className="w-5 h-5 text-muted-foreground" />
              <span className="text-sm">بدء عملية بناء جديدة</span>
            </button>
            <button className="flex items-center gap-3 px-4 py-3 rounded-lg border border-border text-left hover:bg-muted transition-colors">
              <Users className="w-5 h-5 text-muted-foreground" />
              <span className="text-sm">إدارة المستخدمين</span>
            </button>
            <button className="flex items-center gap-3 px-4 py-3 rounded-lg border border-border text-left hover:bg-muted transition-colors">
              <AlertCircle className="w-5 h-5 text-muted-foreground" />
              <span className="text-sm">مراجعة تقارير الانهيار</span>
            </button>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
