/* Copyright (C) 2017 M. Steve Todd mstevetodd@gmail.com

This program is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation; either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

 * This helper class for importing and exporting shared preferences to SD card files.
 * Created by Peter Akers on 17-Dec-17.
*/

package jmri.enginedriver;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class ImportExportPreferences {

    public boolean currentlyImporting = false;

    //private String exportedPreferencesFileName =  "exported_preferences.ed";

    private ArrayList<Integer> engine_address_list;
    private ArrayList<Integer> address_size_list; // Look at address_type.java

    private static final int FORCED_RESTART_REASON_NONE = 0;
//    private static final int FORCED_RESTART_REASON_RESET = 1;
//    private static final int FORCED_RESTART_REASON_LOAD = 2;
//    private static final int FORCED_RESTART_REASON_LOAD_URL = 3;
//    private static final int FORCED_RESTART_REASON_THEME = 4;
//    private static final int FORCED_RESTART_REASON_THROTTLE_PAGE = 5;
//    private static final int FORCED_RESTART_REASON_LOCALE = 6;

    private boolean writeExportFile(Context context, SharedPreferences sharedPreferences, String exportedPreferencesFileName){
        Log.d("Engine_Driver", "ImportExportPreferences: Writing export file");
        boolean res = false;
        ObjectOutputStream output = null;

        File path = Environment.getExternalStorageDirectory();
        File engine_driver_dir = new File(path, "engine_driver");
        engine_driver_dir.mkdir();            // create directory if it doesn't exist

        File dst = new File(path, "engine_driver/"+exportedPreferencesFileName);

        try {
            output = new ObjectOutputStream(new FileOutputStream(dst));
            output.writeObject(sharedPreferences.getAll());
            String m = context.getResources().getString(R.string.toastImportExportExportSucceeded,exportedPreferencesFileName);
            Toast.makeText(context, m, Toast.LENGTH_SHORT).show();
            Log.d("Engine_Driver", m);
            res = true;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (output != null) {
                    output.flush();
                    output.close();
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
        if (!res) {
            Toast.makeText(context, "Export failed!", Toast.LENGTH_LONG).show();
        }
        return res;
    }

    public boolean saveSharedPreferencesToFile(Context context, SharedPreferences sharedPreferences, String exportedPreferencesFileName) {
        Log.d("Engine_Driver", "ImportExportPreferences: Saving preferences to file");
        boolean res = false;

        boolean prefImportExportLocoList = sharedPreferences.getBoolean("prefImportExportLocoList", context.getResources().getBoolean(R.bool.prefImportExportLocoListDefaultValue));
        if (prefImportExportLocoList) {
            engine_address_list = new ArrayList<>();
            address_size_list = new ArrayList<>();
            getRecentLocosListFromFile();
            saveIntListDataToPreferences(engine_address_list, "prefRecentLoco", sharedPreferences);
            saveIntListDataToPreferences(address_size_list, "prefRecentLocoSize", sharedPreferences);
        }

        if (!exportedPreferencesFileName.equals(".ed")) {
            res = writeExportFile(context, sharedPreferences, exportedPreferencesFileName);
        } else {
            Toast.makeText(context, context.getResources().getString(R.string.toastImportExportExportFailed), Toast.LENGTH_LONG).show();
        }

        return res;
    }

    @SuppressLint("ApplySharedPref")
    @SuppressWarnings({ "unchecked" })
    public boolean loadSharedPreferencesFromFile(Context context, SharedPreferences sharedPreferences, String exportedPreferencesFileName, String deviceId) {
        Log.d("Engine_Driver", "ImportExportPreferences: Loading saved preferences from file");
        currentlyImporting = true;
        boolean res = false;
        boolean srcExists = false;
        SharedPreferences.Editor prefEdit = sharedPreferences.edit();

        if (!exportedPreferencesFileName.equals(".ed")) {

            // save a few values so that we can reset them. i.e. efffectively don't import them
            String currentThrottleNameValue = sharedPreferences.getString("throttle_name_preference", context.getResources().getString(R.string.prefThrottleNameDefaultValue)).trim();
            String prefAutoImportExport = sharedPreferences.getString("prefAutoImportExport", context.getResources().getString(R.string.prefAutoImportExportDefaultValue));
            boolean prefForcedRestart = sharedPreferences.getBoolean("prefForcedRestart", false);
            int prefForcedRestartReason = sharedPreferences.getInt("prefForcedRestartReason", FORCED_RESTART_REASON_NONE);
            boolean prefImportExportLocoList = sharedPreferences.getBoolean("prefImportExportLocoList", context.getResources().getBoolean(R.bool.prefImportExportLocoListDefaultValue));


            File path = Environment.getExternalStorageDirectory();
            File engine_driver_dir = new File(path, "engine_driver");

            File src = new File(path, "engine_driver/" + exportedPreferencesFileName);

            if (src.exists()) {
                srcExists = true;

                ObjectInputStream input = null;
                try {
                    input = new ObjectInputStream(new FileInputStream(src));
                    prefEdit.clear();


                    int i = 0;
                    Map<String, ?> entries = (Map<String, ?>) input.readObject();
                    Log.d("Engine_Driver", "loadSharedPreferencesFromFile: Key Count:" + entries.size());
                    for (Map.Entry<String, ?> entry : entries.entrySet()) {
                        Object v = entry.getValue();
                        String key = entry.getKey();

//                        Log.d("Engine_Driver", "ImportExportPreferences: loadSharedPreferencesFromFile: Key Start: " + key);

                        if (v instanceof Boolean) {
//                            Log.d("Engine_Driver", "ImportExportPreferences: loadSharedPreferencesFromFile: Key End: " + key + " - boolean - " + v);
                            prefEdit.putBoolean(key, (Boolean) v);
                        } else if (v instanceof Float) {
//                            Log.d("Engine_Driver", "ImportExportPreferences: loadSharedPreferencesFromFile: Key End: " + key + " - Float - " + v);
                            prefEdit.putFloat(key, (Float) v);
                        } else if (v instanceof Integer) {
//                            Log.d("Engine_Driver", "ImportExportPreferences: loadSharedPreferencesFromFile: Key End: " + key + " - Integer - " + v);
                            prefEdit.putInt(key, (Integer) v);
                        } else if (v instanceof Long) {
//                            Log.d("Engine_Driver", "ImportExportPreferences: loadSharedPreferencesFromFile: Key End: " + key + " - Long - " + v);
                            prefEdit.putLong(key, (Long) v);
                        } else if (v instanceof String) {
//                            Log.d("Engine_Driver", "ImportExportPreferences: loadSharedPreferencesFromFile: Key End: " + key + " - String - " + v);
                            prefEdit.putString(key, ((String) v));
                        }

                        Log.d("Engine_Driver", "ImportExportPreferences: loadSharedPreferencesFromFile: Key " + i +" End: " + key + " - " + v);
                        i++;
                    }
                    res = true;


                    // restore the remembered throttle name to avoid a duplicate throttle name if this is a differnt to device to where it was originally saved
                    String restoredDeviceId = sharedPreferences.getString("prefAndroidId", "").trim();
                    if ((!restoredDeviceId.equals(deviceId)) || (restoredDeviceId.equals(""))) {
                        prefEdit.putString("throttle_name_preference", currentThrottleNameValue);
                    }
                    prefEdit.putString("prefImportExport", "None");  //reset the preference
                    prefEdit.putString("prefHostImportExport", "None");  //reset the preference
                    prefEdit.putString("prefAutoImportExport", prefAutoImportExport);  //reset the preference
                    prefEdit.putBoolean("prefImportExportLocoList", prefImportExportLocoList);  //reset the preference
                    prefEdit.putString("prefRunIntro", threaded_application.INTRO_VERSION);  //don't re-run the intro
                    prefEdit.putBoolean("prefForcedRestart", true);
                    prefEdit.putInt("prefForcedRestartReason", prefForcedRestartReason);

                    String m = context.getResources().getString(R.string.toastImportExportImportSucceeded, exportedPreferencesFileName);

                    Log.d("Engine_Driver", "ImportExportPreferences: " + m);
                    Toast.makeText(context, m, Toast.LENGTH_LONG).show();

                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                    Log.e("Engine_Driver", "ImportExportPreferences: loadSharedPreferencesFromFile: " + e);
                } catch (IOException e) {
                    e.printStackTrace();
                    Log.e("Engine_Driver", "ImportExportPreferences: loadSharedPreferencesFromFile: " + e);
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                    Log.e("Engine_Driver", "ImportExportPreferences: loadSharedPreferencesFromFile: " + e);
                } finally {
                    try {
                        if (input != null) {
                            input.close();
                        }
                    } catch (IOException ex) {
                        ex.printStackTrace();
                        Log.e("Engine_Driver", "ImportExportPreferences: loadSharedPreferencesFromFile: " + ex);
                    }
                }
                currentlyImporting = false;

                if (prefImportExportLocoList) {
                    // now take the recent locos list that was stored in the preferences and push them in the file
                    engine_address_list = new ArrayList<>();
                    address_size_list = new ArrayList<>();
                    getIntListDataFromPreferences(engine_address_list, "prefRecentLoco", sharedPreferences);
                    getIntListDataFromPreferences(address_size_list, "prefRecentLocoSize", sharedPreferences);
                    writeRecentLocosListToFile(sharedPreferences);
                }
            }
            if (!res) {
                if (srcExists) {
                    Toast.makeText(context, context.getResources().getString(R.string.toastImportExportImportFailed, exportedPreferencesFileName), Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(context, context.getResources().getString(R.string.toastImportExportServerImportFailed, exportedPreferencesFileName), Toast.LENGTH_LONG).show();
                }
            }
        } else {
            Toast.makeText(context, context.getResources().getString(R.string.toastImportExportCannotImport), Toast.LENGTH_LONG).show();
        }

        prefEdit.commit();
        return res;
    }

    // simliar, but different, code exists in select_loco.java. if you modify one, make sure you modify the other
    private void getRecentLocosListFromFile() {
        Log.d("Engine_Driver", "ImportExportPreferences: Loading recent locos list from file");
        try {
            // Populate the List with the recent engines saved in a file. This
            // will be stored in /sdcard/engine_driver/recent_engine_list.txt
            File sdcard_path = Environment.getExternalStorageDirectory();
            File engine_list_file = new File(sdcard_path + "/engine_driver/recent_engine_list.txt");
            if (engine_list_file.exists()) {
                BufferedReader list_reader = new BufferedReader(
                        new FileReader(engine_list_file));
                while (list_reader.ready()) {
                    String line = list_reader.readLine();
                    Integer splitPos = line.indexOf(':');
                    if (splitPos > 0) {
                        Integer ea, as;
                        try {
                            ea = Integer.decode(line.substring(0, splitPos));
                            as = Integer.decode(line.substring(splitPos + 1, line.length()));
                        } catch (Exception e) {
                            ea = -1;
                            as = -1;
                        }
                        if ((ea >= 0) && (as >= 0)) {
                            engine_address_list.add(ea);
                            address_size_list.add(as);
                            HashMap<String, String> hm = new HashMap<>();
                        }
                    }
                }
                list_reader.close();
            }

        } catch (IOException except) {
            Log.e("Engine_Driver", "ImportExportPreferences: select_loco - Error reading recent loco file. "
                    + except.getMessage());
        }
    }

    private void writeRecentLocosListToFile(SharedPreferences sharedPreferences) {
        Log.d("Engine_Driver", "ImportExportPreferences: Writing recent locos list to file");

        // write it out from the saved preferences to the file
        File sdcard_path = Environment.getExternalStorageDirectory();
        File engine_list_file = new File(sdcard_path,
                "engine_driver/recent_engine_list.txt");

        PrintWriter list_output;
        String smrl = sharedPreferences.getString("maximum_recent_locos_preference", "10"); //retrieve pref for max recent locos to show
        try {
            int mrl = Integer.parseInt(smrl);
            list_output = new PrintWriter(engine_list_file);
            if (mrl > 0) {
                for (int i = 0; i < engine_address_list.size() && mrl > 0; i++) {
                    list_output.format("%d:%d\n", engine_address_list.get(i), address_size_list.get(i));
                }
            }
            list_output.flush();
            list_output.close();
        } catch (IOException except) {
            Log.e("Engine_Driver",
                    "select_loco - Error creating a PrintWriter, IOException: "
                            + except.getMessage());
        }
    }

    private boolean saveIntListDataToPreferences(ArrayList<Integer> list, String listName, SharedPreferences sharedPreferences) {
        sharedPreferences.edit().putInt(listName +"_size", list.size()).commit();
        int prefInt;
        for(int i=0 ; i<list.size() ; i++){
            prefInt=list.get(i);
            sharedPreferences.edit().putInt(listName + "_" + i, prefInt).commit();
        }
        return sharedPreferences.edit().commit();
    }

    public int getIntListDataFromPreferences(ArrayList<Integer> list, String listName, SharedPreferences sharedPreferences) {
        int size = sharedPreferences.getInt(listName + "_size", 0);
        int prefInt;
        for(int i=0 ; i<size ; i++){
            prefInt = sharedPreferences.getInt(listName + "_" + i, 0);
            list.add(prefInt);
        }
        return size;
    }

}
