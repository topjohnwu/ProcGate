package com.topjohnwu.procgate;

import android.app.Activity;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.topjohnwu.superuser.Shell;
import com.topjohnwu.superuser.io.SuFile;
import com.topjohnwu.superuser.io.SuFileOutputStream;

import java.io.IOException;

import androidx.annotation.Keep;

public class MainActivity extends Activity {

    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("native-lib");
    }

    private TextView text;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_layout);
        text = findViewById(R.id.text);
        text.setMovementMethod(new ScrollingMovementMethod());
        text.setHorizontallyScrolling(true);
    }

    @Keep
    private void addText(String s) {
        text.append(s);
    }

    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */
    public native void inspectProcFS();

    public void onClick(View view) {
        text.setText("");
        inspectProcFS();
    }

    public void remount(View view) {
        if (!Shell.rootAccess()) {
            Toast.makeText(this, "No root access detected", Toast.LENGTH_SHORT).show();
        } else {
            if (Shell.su("mount -o remount,hidepid=2,gid=3009 /proc").exec().isSuccess())
                Toast.makeText(this, "Remount success", Toast.LENGTH_SHORT).show();
            else
                Toast.makeText(this, "Remount failed", Toast.LENGTH_SHORT).show();
        }
    }

    public void inject(View view) {
        if (!Shell.rootAccess())
            Toast.makeText(this, "No root access detected", Toast.LENGTH_SHORT).show();
        else {
            SuFile dir = new SuFile("/sbin/.core/img/.core/post-fs-data.d");
            if (!dir.exists())
                dir = new SuFile("/su/su.d");
            if (!dir.exists())
                Toast.makeText(this, "Cannot find location to place boot scripts",
                        Toast.LENGTH_SHORT).show();
            else {
                SuFile script = new SuFile(dir.getPath(), "procfix.sh");
                try (SuFileOutputStream out = new SuFileOutputStream(script)) {
                    out.write("#!/system/bin/sh\n".getBytes());
                    out.write("mount -o remount,hidepid=2,gid=3009 /proc\n".getBytes());
                } catch (IOException e) {
                    Toast.makeText(this, "Script addition failed", Toast.LENGTH_SHORT).show();
                    e.printStackTrace();
                    return;
                }
                Toast.makeText(this, "Script added", Toast.LENGTH_SHORT).show();
                script.setExecutable(true, false);
            }
        }
    }
}
