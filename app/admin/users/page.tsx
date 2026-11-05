import { db } from "@/lib/db";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Plus } from "lucide-react";
import { DataTable, StatusBadge } from "@/components/admin/data-table";
import { format } from "date-fns";

export default async function UsersPage() {
  const users = await db.user.findMany({
    orderBy: { createdAt: "desc" },
  });

  return (
    <div className="space-y-6">
      <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold">إدارة المستخدمين</h1>
          <p className="text-muted-foreground">إدارة حسابات المستخدمين وأدوارهم في النظام</p>
        </div>
        <Button className="w-fit">
          <Plus className="w-4 h-4 mr-2" />
          إضافة مستخدم
        </Button>
      </div>

      <div className="flex items-center gap-2 mb-4">
        <Input placeholder="البحث عن مستخدم..." className="w-80 h-9" />
      </div>

      <Card>
        <CardHeader>
          <CardTitle>قائمة المستخدمين</CardTitle>
        </CardHeader>
        <CardContent>
          <DataTable
            data={users}
            columns={[
              { key: "name", header: "الاسم" },
              { key: "email", header: "البريد الإلكتروني" },
              { key: "role", header: "الدور", render: (row) => <StatusBadge status={row.role} /> },
              {
                key: "isActive",
                header: "الحالة",
                render: (row) => <StatusBadge status={row.isActive ? "نشط" : "غير نشط"} />,
              },
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
