import { db } from "@/lib/db";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { DataTable, StatusBadge } from "@/components/admin/data-table";
import { format } from "date-fns";

export default async function SupportPage() {
  const tickets = await db.supportTicket.findMany({
    include: { user: true, assignedToUser: true, _count: { select: { messages: true } } },
    orderBy: { updatedAt: "desc" },
  });

  return (
    <div className="space-y-6">
      <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold">تذاكر الدعم الفني</h1>
          <p className="text-muted-foreground">إدارة ومتابعة تذاكر دعم المستخدمين</p>
        </div>
        <Button className="w-fit">تذكرة جديدة</Button>
      </div>

      <div className="flex items-center gap-2 mb-4">
        <Input placeholder="البحث عن تذكرة..." className="w-80 h-9" />
      </div>

      <Card>
        <CardHeader>
          <CardTitle>قائمة التذاكر</CardTitle>
        </CardHeader>
        <CardContent>
          <DataTable
            data={tickets}
            columns={[
              { key: "ticketNumber", header: "رقم التذكرة" },
              { key: "subject", header: "الموضوع" },
              {
                key: "status",
                header: "الحالة",
                render: (row) => <StatusBadge status={row.status} />,
              },
              {
                key: "priority",
                header: "الأولوية",
                render: (row) => <StatusBadge status={row.priority} />,
              },
              {
                key: "user",
                header: "المستخدم",
                render: (row: any) => row.user?.name ?? "-",
              },
              {
                key: "updatedAt",
                header: "آخر تحديث",
                render: (row) => format(new Date(row.updatedAt), "MMM dd, yyyy HH:mm"),
              },
            ]}
          />
        </CardContent>
      </Card>
    </div>
  );
}
