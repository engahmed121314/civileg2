import { db } from "@/lib/db";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Plus } from "lucide-react";
import { DataTable, StatusBadge } from "@/components/admin/data-table";
import { format } from "date-fns";

export default async function AnnouncementsPage() {
  const announcements = await db.announcement.findMany({
    include: { author: true },
    orderBy: { createdAt: "desc" },
  });

  return (
    <div className="space-y-6">
      <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold">الإعلانات</h1>
          <p className="text-muted-foreground">إدارة الإعلانات التي تُعرض على المستخدمين</p>
        </div>
        <Button className="w-fit">
          <Plus className="w-4 h-4 mr-2" />
          إعلان جديد
        </Button>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>قائمة الإعلانات</CardTitle>
        </CardHeader>
        <CardContent>
          <DataTable
            data={announcements}
            columns={[
              { key: "title", header: "العنوان" },
              { key: "type", header: "النوع", render: (row) => <StatusBadge status={row.type} /> },
              { key: "isActive", header: "نشط", render: (row) => <StatusBadge status={row.isActive ? "نعم" : "لا"} /> },
              {
                key: "author",
                header: "الكاتب",
                render: (row: any) => row.author?.name ?? "-",
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
