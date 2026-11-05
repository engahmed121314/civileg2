import { db } from "@/lib/db";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Plus } from "lucide-react";
import { DataTable, StatusBadge } from "@/components/admin/data-table";
import { format } from "date-fns";

export default async function VersionsPage() {
  const versions = await db.appVersion.findMany({
    include: { app: true, _count: { select: { builds: true } } },
    orderBy: { createdAt: "desc" },
  });

  return (
    <div className="space-y-6">
      <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold">إدارة الإصدارات</h1>
          <p className="text-muted-foreground">إدارة إصدارات التطبيقات وأكواد البناء</p>
        </div>
        <Button className="w-fit">
          <Plus className="w-4 h-4 mr-2" />
          إصدار جديد
        </Button>
      </div>

      <div className="flex items-center gap-2 mb-4">
        <Input placeholder="البحث عن إصدار..." className="w-80 h-9" />
      </div>

      <Card>
        <CardHeader>
          <CardTitle>قائمة الإصدارات</CardTitle>
        </CardHeader>
        <CardContent>
          <DataTable
            data={versions}
            columns={[
              { key: "version", header: "رقم الإصدار" },
              { key: "buildCode", header: "كود البناء" },
              {
                key: "app",
                header: "التطبيق",
                render: (row: any) => row.app?.name ?? "-",
              },
              { key: "isActive", header: "نشط", render: (row) => <StatusBadge status={row.isActive ? "نعم" : "لا"} /> },
              {
                key: "createdAt",
                header: "تاريخ الإنشاء",
                render: (row) => format(new Date(row.createdAt), "MMM dd, yyyy"),
              },
            ]}
          />
        </CardContent>
      </Card>
    </div>
  );
}
