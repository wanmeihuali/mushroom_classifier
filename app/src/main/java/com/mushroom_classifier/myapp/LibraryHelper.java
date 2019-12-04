package com.mushroom_classifier.myapp;

import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import android.content.Context;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import com.mushroom_classifier.myapp.CategoryInfo;

class LibraryHelper extends SQLiteOpenHelper {
    private final static int VERSION = 1;


    public LibraryHelper(Context context) throws SQLException{
        //require a Context, Path.getLibraryPath() return the absolute path of db file.
        super(context, "fungi.db", null, VERSION);
    }

    public void execSQL(String sql){
        // query = "UPDATE ..." or "DELETE... "
        try {
            SQLiteDatabase db = getWritableDatabase();
            execSQLCmd(db,sql);
        }catch (SQLException e){
            e.printStackTrace();
            throw e;
        }
    }

    public CategoryInfo gainData(String rawQuery){
        Cursor cursor = execQuery(rawQuery);
        int cat_id_idx = cursor.getColumnIndex("id");
        int cat_name_idx = cursor.getColumnIndex("name");
        int info_idx = cursor.getColumnIndex("info");
        int image_idx = cursor.getColumnIndex("example_img");
        // each type has a specific getter

        CategoryInfo ret = new CategoryInfo();
        while(cursor.moveToNext()){
            ret.cat_id = cursor.getInt(cat_id_idx);
            ret.cat_name = cursor.getString(cat_name_idx);
            ret.info = cursor.getString(info_idx);
            ret.image = cursor.getBlob(image_idx);
        }
        cursor.close();
        return ret;
    }

    public Cursor execQuery(String query) throws SQLException{
        // query = "SELECT ... FROM ..."
        try {
            SQLiteDatabase db = getReadableDatabase();
            return db.rawQuery(query, null);
        }catch (SQLException e){
            e.printStackTrace();
            throw e;
        }
    }


    @Override
    public void onCreate(SQLiteDatabase db) {

        // execSQLFile(db,getInitScript());
        //run once, only execute when db create
    }


    @Override
    public void onOpen(SQLiteDatabase db){
        super.onOpen(db);
        db.execSQL("PRAGMA foreign_keys = ON;");
        //every time open db, execute
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        //not used right now
    }

    /*
    // open  'init.sql' file stored in assets folder
    private static InputStream getInitScript(){
        try {
            return CodeRunTime.getContext().getAssets().open("init.sql");
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
    */

    // work with special db object
    private static void execSQLCmd(SQLiteDatabase db,String str){
        String[] cmds = str.split(";");
        for (String cmd : cmds) {
            db.execSQL(cmd);
        }
    }

    //run '.sql' file
    private static void execSQLFile(SQLiteDatabase db, InputStream stream){
        try {
            InputStreamReader reader = new InputStreamReader(stream);
            BufferedReader bufferedReader = new BufferedReader(reader);
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                builder.append(line);
                builder.append('\n');
            }
            execSQLCmd(db,builder.toString());
        }catch (IOException IOE){
            IOE.printStackTrace();
        }catch (SQLException SQLE){
            SQLE.printStackTrace();
        }
    }

}
