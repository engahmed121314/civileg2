import { db } from "@/lib/db";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { DataTable, StatusBadge } from "@/components/admin/data-table";
import { format } from "date-fns";

export default async function DevicesPage() {
  const devices = await db.device.findMany({
    include: { app: true },
    orderBy: { lastSeenAt: "desc" },
  });

  return (
    <div className="space-y-6">
      <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold">إدارة الأجهزة</h1>
          <p className="text-muted-foreground">مراقبة الأجهزة المثبت عليها التطبيق ونشاطها</p>
        </div>
        <Button className="w-fit">تصدير CSV</Button>
      </div>

      <div className="flex items-center gap-2 mb-4">
        <Input placeholder="البحث عن جهاز..." className="w-80 h-9" />
      </div>

      <Card>
        <CardHeader>
          <CardTitle>قائمة الأجهزة المتصلة</CardTitle>
        </CardHeader>
        <CardContent>
          <DataTable
            data={devices}
            columns={[
              { key: "deviceId", header: "معرف الجهاز" },
              { key: "model", header: "الموديل" },
              { key: "brand", header: "العلامة التجارية" },
              { key: "osVersion", header: "إصدار النظام" },
              { key: "appVersion", header: "إصدار التطبيق" },
              { key: "isActive", header: "نشط", render: (row) => <StatusBadge status={row.isActive ? "نعم" : "لا"} /> },
              {
                key: "lastSeenAt",
                header: "آخر ظهور",
                render: (row) => (row.lastSeenAt ? format(new Date(row.lastSeenAt), "MMM dd, yyyy HH:mm") : "-"),
              },
            ]}
          />
        </CardContent>
      </Card>
    </div>
  );
}
