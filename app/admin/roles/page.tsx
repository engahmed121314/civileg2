import { db } from "@/lib/db";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Plus } from "lucide-react";
import { DataTable } from "@/components/admin/data-table";
import { format } from "date-fns";

const roles = [
  { id: "1", role: "ADMIN", name: "مسؤول النظام", permissions: ["كل الصلاحيات"], usersCount: 2 },
  { id: "2", role: "DEVELOPER", name: "مطور", permissions: ["إدارة التطبيقات", "البناء", "تقارير الانهيار"], usersCount: 5 },
  { id: "3", role: "MODERATOR", name: "مشرف", permissions: ["إدارة المستخدمين", "تذاكر الدعم", "الإعلانات"], usersCount: 3 },
  { id: "4", role: "VIEWER", name: "مشاهد", permissions: ["عرض فقط"], usersCount: 10 },
];

export default function RolesPage() {
  return (
    <div className="space-y-6">
      <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold">الأدوار والصلاحيات</h1>
          <p className="text-muted-foreground">إدارة أدوار المستخدمين وصلاحياتهم في النظام</p>
        </div>
        <Button className="w-fit">
          <Plus className="w-4 h-4 mr-2" />
          دور جديد
        </Button>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>قائمة الأدوار</CardTitle>
        </CardHeader>
        <CardContent>
          <DataTable
            data={roles}
            columns={[
              { key: "name", header: "اسم الدور" },
              { key: "role", header: "المعرف التقني" },
              {
                key: "permissions",
                header: "الصلاحيات",
                render: (row: any) => row.permissions.join(", "),
              },
              {
                key: "usersCount",
                header: "عدد المستخدمين",
              },
              {
                key: "createdAt",
                header: "تاريخ الإنشاء",
                render: () => format(new Date(), "MMM dd, yyyy"),
              },
            ]}
          />
        </CardContent>
      </Card>
    </div>
  );
}
