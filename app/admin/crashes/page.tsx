import { db } from "@/lib/db";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { DataTable, StatusBadge } from "@/components/admin/data-table";
import { format } from "date-fns";

export default async function CrashesPage() {
  const crashes = await db.crashReport.findMany({
    include: { app: true, build: true, device: true },
    orderBy: { occurredAt: "desc" },
  });

  return (
    <div className="space-y-6">
      <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold">تقارير الانهيار</h1>
          <p className="text-muted-foreground">مراجعة وتحليل تقارير انهيار التطبيق</p>
        </div>
        <Button className="w-fit">تصدير تقرير</Button>
      </div>

      <div className="flex items-center gap-2 mb-4">
        <Input placeholder="البحث عن تقرير..." className="w-80 h-9" />
      </div>

      <Card>
        <CardHeader>
          <CardTitle>قائمة تقارير الانهيار</CardTitle>
        </CardHeader>
        <CardContent>
          <DataTable
            data={crashes}
            columns={[
              { key: "title", header: "العنوان" },
              {
                key: "severity",
                header: "الشدة",
                render: (row) => <StatusBadge status={row.severity} />,
              },
              {
                key: "isResolved",
                header: "الحالة",
                render: (row) => <StatusBadge status={row.isResolved ? "محلول" : "غير محلول"} />,
              },
              {
                key: "app",
                header: "التطبيق",
                render: (row: any) => row.app?.name ?? "-",
              },
              {
                key: "occurredAt",
                header: "وقت حدوثه",
                render: (row) => format(new Date(row.occurredAt), "MMM dd, yyyy HH:mm"),
              },
            ]}
          />
        </CardContent>
      </Card>
    </div>
  );
}
