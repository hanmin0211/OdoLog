// 오도로그 서버 실행 파일: 같은 폴더의 runtime(Java)과 lib(서버)로 서버를 켜고, 데이터는 같은 폴더의 data 에 둠
// 이 창을 닫으면 서버도 꺼짐
using System;
using System.Diagnostics;
using System.IO;

class OdoLogServer
{
    static int Main()
    {
        string dir = AppDomain.CurrentDomain.BaseDirectory;
        string java = Path.Combine(dir, "runtime", "bin", "java.exe");
        if (!File.Exists(java))
        {
            Console.WriteLine("runtime 폴더가 없습니다. 오도로그 서버 폴더를 통째로 복사했는지 확인하세요.");
            WaitKey();
            return 1;
        }
        Console.Title = "오도로그 서버";
        var p = new ProcessStartInfo(java, "-Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp \"" + Path.Combine(dir, "lib", "*") + "\" odolog.server.MainKt");
        p.UseShellExecute = false;
        p.WorkingDirectory = dir;
        p.EnvironmentVariables["ODOLOG_DATA"] = Path.Combine(dir, "data");
        p.EnvironmentVariables["ODOLOG_OPEN"] = "1";
        Console.OutputEncoding = System.Text.Encoding.UTF8;
        using (var proc = Process.Start(p))
        {
            proc.WaitForExit();
            if (proc.ExitCode != 0)
            {
                Console.WriteLine("서버가 오류로 멈췄습니다. 아무 키나 누르면 창이 닫힙니다.");
                WaitKey();
            }
            return proc.ExitCode;
        }
    }

    // 안내를 읽을 수 있게 창을 잡아 둠 (키보드 입력이 없는 실행이면 바로 끝냄)
    static void WaitKey()
    {
        if (!Console.IsInputRedirected) Console.ReadKey();
    }
}
