import { db } from "@/lib/db";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Plus, Play } from "lucide-react";
import { DataTable, StatusBadge } from "@/components/admin/data-table";
import { format } from "date-fns";

export default async function BuildsPage() {
  const builds = await db.build.findMany({
    include: { app: true, version: true, triggeredByUser: true },
    orderBy: { createdAt: "desc" },
  });

  return (
    <div className="space-y-6">
      <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold">إدارة构建ات التطبيق</h1>
          <p className="text-muted-foreground">مراقبة وإدارة عمليات بناء التطبيقات وإصداراتها</p>
        </div>
        <div className="flex gap-2">
          <Button className="w-fit">
            <Plus className="w-4 h-4 mr-2" />
            بناء جديد
          </Button>
          <Button variant="outline" className="w-fit">
            <Play className="w-4 h-4 mr-2" />
            تشغيل البناء
          </Button>
        </div>
      </div>

      <div className="flex items-center gap-2 mb-4">
        <Input placeholder="البحث عن بناء..." className="w-80 h-9" />
      </div>

      <Card>
        <CardHeader>
          <CardTitle>قائمة构建ات التطبيق</CardTitle>
        </CardHeader>
        <CardContent>
          <DataTable
            data={builds}
            columns={[
              { key: "version", header: "الإصدار" },
              {
                key: "channel",
                header: "القناة",
                render: (row) => <StatusBadge status={row.channel} />,
              },
              {
                key: "status",
                header: "الحالة",
                render: (row) => <StatusBadge status={row.status} />,
              },
              {
                key: "app",
                header: "التطبيق",
                render: (row: any) => row.app?.name ?? "-",
              },
              {
                key: "createdAt",
                header: "تاريخ الإنشاء",
                render: (row) => format(new Date(row.createdAt), "MMM dd, yyyy HH:mm"),
              },
            ]}
          />
        </CardContent>
      </Card>
    </div>
  );
}
