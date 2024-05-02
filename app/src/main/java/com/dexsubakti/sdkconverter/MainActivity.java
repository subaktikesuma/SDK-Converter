package com.dexsubakti.sdkconverter;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;

import com.dexsubakti.sdkconverter.databinding.ActivityMainBinding;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {

    // Used to load the 'sdkconverter' library on application startup.
    static {
        System.loadLibrary("sdkconverter");
    }

    private ActivityMainBinding binding;

    TextView ImportedFileText;

    static String textFromFile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Example of a call to a native method
        ImportedFileText = binding.sampleText;
        binding.importFile.setOnClickListener(v -> {
            openFile();
        });
        binding.startButton.setOnClickListener(v -> {
            saveFile();
            Log.i("Subakti", textFromFile);
        });
    }

    public static String convertCode(String inputCode) {
        StringBuilder output = new StringBuilder();

        output.append("namespace Offsets {\n");

        // Extract struct definitions
        Pattern structPattern = Pattern.compile("struct\\s+(\\w+)\\s*:\\s*\\w+\\s*\\{([^\\}]+)\\}");
        Matcher structMatcher = structPattern.matcher(inputCode);

        while (structMatcher.find()) {
            String structName = structMatcher.group(1);
            String structFields = structMatcher.group(2).trim();

            output.append("\tnamespace ").append(structName).append(" {\n");

            // Extract fields for current struct
            Pattern fieldPattern = Pattern.compile("\\t(?!\\/\\/\\s*Fields)([^;]+);\\s*\\/\\/\\s*Offset:\\s*(0x\\w+)\\s*\\|\\s*Size:\\s*(0x\\w+)");
            Matcher fieldMatcher = fieldPattern.matcher(structFields);

            output.append("\t\t\t// Fields\n");
            while (fieldMatcher.find()) {
                String fieldDeclaration = fieldMatcher.group(1).replace(" : ", "_");
                String offset = fieldMatcher.group(2);
                String size = fieldMatcher.group(3);

                // Split field declaration into type and name
                String[] parts = fieldDeclaration.trim().split("\\s+");
                String fieldType = parts[0]; // Field type
                String fieldName = parts[parts.length - 1].split("\\[")[0]; // Field name (remove array indicator if present)

                // Append field declaration to output
                output.append("\t\t\tuintptr_t ").append(fieldName).append(" = ").append(offset).append("; //").append(fieldDeclaration).append("\n");
            }

            output.append("\t}\n\n");
        }
        output.append("}\n\n");

        return output.toString();
    }

    private void openFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");  // All file types
        startActivityForResult(intent, 123);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 123 && resultCode == Activity.RESULT_OK) {
            Uri uri = data.getData();
            String filePath = FileUtils.getPathFromUri(this, uri);
            readContent(uri);
            ImportedFileText.setText("Imported File : " + filePath);
        }
    }

    private void readContent(Uri uri) {
        try {
            StringBuilder content = new StringBuilder();
            InputStream inputStream = getContentResolver().openInputStream(uri);
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
            reader.close();
            inputStream.close();
            textFromFile = content.toString();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void saveFile() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TITLE, "SDK.h");
        saveFileLauncher.launch(intent);
    }

    private final ActivityResultLauncher<Intent> saveFileLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                        if (result.getResultCode() == RESULT_OK) {
                            Intent data = result.getData();
                            if (data != null) {
                                Uri uri = data.getData();
                                if (uri != null) {
                                    writeToFile(uri);
                                }
                            }
                        }
                    });

    private void writeToFile(Uri uri) {
        try {
            DocumentFile documentFile = DocumentFile.fromSingleUri(this, uri);
            if (documentFile != null && documentFile.canWrite()) {
                try (FileOutputStream fos = (FileOutputStream) getContentResolver().openOutputStream(uri)) {
                    String content = convertCode(textFromFile);
                    fos.write(content.getBytes());
                    Toast.makeText(this, "File saved successfully", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "Cannot write to the selected file", Toast.LENGTH_SHORT).show();
            }
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error saving file", Toast.LENGTH_SHORT).show();
        }
    }


    /**
     * A native method that is implemented by the 'sdkconverter' native library,
     * which is packaged with this application.
     */
    public native String stringFromJNI();
}