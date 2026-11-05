import { db } from "@/lib/db";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Plus } from "lucide-react";
import { DataTable, StatusBadge } from "@/components/admin/data-table";
import { format } from "date-fns";

export default async function AppsPage() {
  const apps = await db.app.findMany({
    include: {
      _count: { select: { versions: true, builds: true, devices: true, crashReports: true } },
    },
    orderBy: { createdAt: "desc" },
  });

  return (
    <div className="space-y-6">
      <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold">إدارة التطبيقات</h1>
          <p className="text-muted-foreground">إدارة وإنشاء التطبيقات التي تخضع للتطوير</p>
        </div>
        <Button className="w-fit">
          <Plus className="w-4 h-4 mr-2" />
          إضافة تطبيق
        </Button>
      </div>

      <div className="flex items-center gap-2 mb-4">
        <Input placeholder="البحث عن تطبيق..." className="w-80 h-9" />
      </div>

      <Card>
        <CardHeader>
          <CardTitle>قائمة التطبيقات</CardTitle>
        </CardHeader>
        <CardContent>
          <DataTable
            data={apps}
            columns={[
              { key: "name", header: "اسم التطبيق" },
              { key: "packageName", header: "اسم الحزمة" },
              { key: "status", header: "الحالة", render: (row) => <StatusBadge status={row.status} /> },
              {
                key: "createdAt",
                header: "تاريخ الإنشاء",
                render: (row) => format(new Date(row.createdAt), "MMM dd, yyyy"),
              },
              {
                key: "_count",
                header: "الإحصائيات",
                render: (row: any) =>
                  `إصدارات: ${row._count.versions} • بنى: ${row._count.builds} • أجهزة: ${row._count.devices} • انهيارات: ${row._count.crashReports}`,
              },
            ]}
          />
        </CardContent>
      </Card>
    </div>
  );
}
