import { getServerSession } from "next-auth";
import { redirect } from "next/navigation";
import { authOptions } from "@/lib/auth";
import { AdminSidebar } from "@/components/admin/sidebar";
import { Toaster } from "@/components/ui/toaster";

export const dynamic = "force-dynamic";

export default async function AdminLayout({
  children,
}: Readonly<{ children: React.ReactNode }>) {
  const session = await getServerSession(authOptions);

  const userRole = (session?.user as any)?.role;

  if (!session || !userRole) {
    redirect("/auth/signin");
  }

  return (
    <div className="flex h-screen bg-background">
      <AdminSidebar />
      <div className="flex-1 flex flex-col overflow-hidden">
        <header className="flex items-center justify-between px-6 py-4 border-b border-border bg-background/50 backdrop-blur sticky top-0 z-20">
          <div className="flex items-center gap-4">
            <h1 className="text-xl font-bold">لوحة الإدارة</h1>
          </div>
          <div className="flex items-center gap-3">
            <Toaster />
            <div className="hidden md:flex items-center gap-2 px-3 py-1.5 rounded-full bg-muted text-xs text-muted-foreground border border-border">
              <span className="w-2 h-2 rounded-full bg-emerald-500" />
              متصل
            </div>
          </div>
        </header>
        <main className="flex-1 overflow-y-auto p-6">
          {children}
        </main>
      </div>
    </div>
  );
}
