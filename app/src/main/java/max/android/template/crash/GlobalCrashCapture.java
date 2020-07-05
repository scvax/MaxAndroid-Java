package max.android.template.crash;

import android.app.Application;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.annotation.NonNull;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import max.android.template.BuildConfig;
import max.android.template.R;
import max.android.template.logger.Log;
import max.android.template.worker.Worker;

/**
 * Created by: var_rain.
 * Created date: 2018/11/7.
 * Description: 全局异常捕获
 */
public class GlobalCrashCapture implements Thread.UncaughtExceptionHandler {

    // 静态实例
    private static GlobalCrashCapture INS;
    // 应用程序的上下文参数
    private Application context;
    // looper状态
    private boolean running = true;
    // 日志储存路径
    private String savePath;
    // 日期时间格式
    private SimpleDateFormat simpleDateFormat;

    /**
     * 构造方法
     */
    private GlobalCrashCapture() {
        this.savePath = Environment.getExternalStorageDirectory().getPath() + "/crash/";
        this.simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.getDefault());
    }

    /**
     * 初始化
     *
     * @param context 上下文参数
     */
    public void init(Application context) {
        this.context = context;
        this.looperException();
        Thread.setDefaultUncaughtExceptionHandler(this);
    }

    /**
     * 获取当前对象的静态实例
     *
     * @return 返回 {@link GlobalCrashCapture} 对象
     */
    public static GlobalCrashCapture ins() {
        if (GlobalCrashCapture.INS == null) {
            GlobalCrashCapture.INS = new GlobalCrashCapture();
        }
        return GlobalCrashCapture.INS;
    }

    /**
     * Android主线程异常捕获
     */
    private void looperException() {
        new Handler(Looper.getMainLooper()).post(() -> {
            while (running) {
                try {
                    Looper.loop();
                } catch (Throwable e) {
                    this.handleException(e);
                }
            }
        });
    }

    @Override
    public void uncaughtException(@NonNull Thread t, @NonNull Throwable e) {
        this.handleException(e);
    }

    /**
     * 默认异常信息处理
     *
     * @param ex 异常信息
     */
    private void handleException(Throwable ex) {
        Worker.execute(() -> {
            Looper.prepare();
            ex.printStackTrace();
            if (BuildConfig.DEBUG) {
                Toast.makeText(context, ex.getLocalizedMessage(), Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(context, R.string.app_exception, Toast.LENGTH_SHORT).show();
//                this.delay();
//                Meter.ins().exit();
            }
            this.dumpException(ex);
            Looper.loop();
        });
    }

    /**
     * 延时
     */
    @SuppressWarnings("unused")
    private void delay() {
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * 将异常信息收集并保存到文件
     *
     * @param throwable 异常信息
     */
    private void dumpException(Throwable throwable) {
        if (!extStorageCanSave()) return;
        String time = simpleDateFormat.format(new Date(System.currentTimeMillis()));
        File file = new File(savePath + "crash-" + time + ".log");
        Log.d("log file path: " + file.getPath());
        try {
            PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(file)));
            writer.println(time);
            dumpDeviceInfo(writer);
            writer.println();
            throwable.printStackTrace(writer);
            writer.close();
        } catch (Exception e) {
            Log.e("dump exception failed: %s", e);
        }
    }

    /**
     * 检查外部储存是否可用,可用则检查文件夹是否存在,不存在则创建
     *
     * @return true: 可用 false: 不可用
     */
    private boolean extStorageCanSave() {
        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            File dir = new File(savePath);
            if (!dir.exists()) {
                boolean isSuccess = dir.mkdir();
                Log.d("create directory is success: " + isSuccess);
            }
            return true;
        }
        Log.w("sdcard unmounted, skip dump exception to sdcard");
        return false;
    }

    /**
     * 收集设备信息
     *
     * @param writer 输出流
     * @throws PackageManager.NameNotFoundException 调用 {@link PackageManager#getPackageInfo(String, int)} 时可能会出现的异常
     */
    private void dumpDeviceInfo(PrintWriter writer) throws PackageManager.NameNotFoundException {
        PackageInfo packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), PackageManager.GET_ACTIVITIES);
        writer.println("App Version: " + packageInfo.versionName + "_" + packageInfo.versionCode);
        writer.println("Android OS Version: " + Build.VERSION.RELEASE + "_" + Build.VERSION.SDK_INT);
        writer.println("Device Vendor: " + Build.MANUFACTURER);
        writer.println("Device Mode: " + Build.MODEL);
        dumpDeviceABIs32(writer);
        dumpDeviceABIs64(writer);
    }

    /**
     * 导出设备支持的32位架构
     *
     * @param writer 输出流
     */
    private void dumpDeviceABIs32(PrintWriter writer) {
        writer.print("Device CPU ARCH 32 : ");
        String[] abi = Build.SUPPORTED_32_BIT_ABIS;
        if (abi != null) {
            for (String arch : abi) {
                writer.print(arch);
                writer.print(" ");
            }
        }
        writer.println();
    }

    /**
     * 导出设备支持的64位架构
     *
     * @param writer 输出流
     */
    private void dumpDeviceABIs64(PrintWriter writer) {
        writer.print("Device CPU ARCH 64 : ");
        String[] abi = Build.SUPPORTED_64_BIT_ABIS;
        if (abi != null) {
            for (String arch : abi) {
                writer.print(arch);
                writer.print(" ");
            }
        }
        writer.println();
    }
}
