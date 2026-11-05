import { db } from "@/lib/db";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { DataTable } from "@/components/admin/data-table";
import { format } from "date-fns";

export default async function LogsPage() {
  const logs = await db.activityLog.findMany({
    include: { user: true },
    orderBy: { createdAt: "desc" },
    take: 100,
  });

  return (
    <div className="space-y-6">
      <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold">سجلات النشاط</h1>
          <p className="text-muted-foreground">مراجعة سجلات الأنشطة والنظام</p>
        </div>
        <Button className="w-fit">تصدير السجلات</Button>
      </div>

      <div className="flex items-center gap-2 mb-4">
        <Input placeholder="البحث في السجلات..." className="w-80 h-9" />
      </div>

      <Card>
        <CardHeader>
          <CardTitle>آخر السجلات</CardTitle>
        </CardHeader>
        <CardContent>
          <DataTable
            data={logs}
            columns={[
              { key: "action", header: "الإجراء" },
              {
                key: "entityType",
                header: "نوع الكيان",
                render: (row) => row.entityType ?? "-",
              },
              {
                key: "user",
                header: "المستخدم",
                render: (row: any) => row.user?.name ?? "-",
              },
              {
                key: "ipAddress",
                header: "عنوان IP",
                render: (row) => row.ipAddress ?? "-",
              },
              {
                key: "createdAt",
                header: "الوقت",
                render: (row) => format(new Date(row.createdAt), "MMM dd, yyyy HH:mm:ss"),
              },
            ]}
          />
        </CardContent>
      </Card>
    </div>
  );
}
