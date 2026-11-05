"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Switch } from "@/components/ui/switch";
import { Separator } from "@/components/ui/separator";

export default function SettingsPage() {
  const [updates, setUpdates] = useState(true);
  const [crashReports, setCrashReports] = useState(true);
  const [analytics, setAnalytics] = useState(false);
  const [maintenance, setMaintenance] = useState(false);

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold">إعدادات النظام</h1>
        <p className="text-muted-foreground">ضبط إعدادات التطبيق والنظام العام</p>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>الإعدادات العامة</CardTitle>
        </CardHeader>
        <CardContent className="space-y-6">
          <div className="space-y-4">
            <div className="flex items-center justify-between">
              <div>
                <Label className="font-medium">تفعيل التحديثات التلقائية</Label>
                <p className="text-xs text-muted-foreground">السماح بالتحديث التلقائي للتطبيقات المثبتة</p>
              </div>
              <Switch checked={updates} onCheckedChange={setUpdates} />
            </div>
            <Separator />
            <div className="flex items-center justify-between">
              <div>
                <Label className="font-medium">إرسال تقارير الانهيار</Label>
                <p className="text-xs text-muted-foreground">إرسال تقارير الانهيار تلقائياً للخادم</p>
              </div>
              <Switch checked={crashReports} onCheckedChange={setCrashReports} />
            </div>
            <Separator />
            <div className="flex items-center justify-between">
              <div>
                <Label className="font-medium">التحليلات والإحصائيات</Label>
                <p className="text-xs text-muted-foreground">جمع بيانات استخدام التطبيق للتحليل</p>
              </div>
              <Switch checked={analytics} onCheckedChange={setAnalytics} />
            </div>
            <Separator />
            <div className="flex items-center justify-between">
              <div>
                <Label className="font-medium">وضع الصيانة</Label>
                <p className="text-xs text-muted-foreground">إيقاف التطبيق مؤقتاً للصيانة</p>
              </div>
              <Switch checked={maintenance} onCheckedChange={setMaintenance} />
            </div>
          </div>
        </CardContent>
        <CardContent className="pt-0">
          <Button className="w-fit">حفظ التغييرات</Button>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>إعدادات الخادم</CardTitle>
        </CardHeader>
        <CardContent className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div className="space-y-2">
            <Label className="text-sm font-medium">رابط قاعدة البيانات</Label>
            <Input placeholder="sqlite://./dev.db" className="h-9" />
          </div>
          <div className="space-y-2">
            <Label className="text-sm font-medium">المنفذ</Label>
            <Input type="number" placeholder="3000" className="h-9" />
          </div>
          <div className="space-y-2">
            <Label className="text-sm font-medium">سرية الجلسة (NEXTAUTH_SECRET)</Label>
            <Input type="password" placeholder="••••••••" className="h-9" />
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
