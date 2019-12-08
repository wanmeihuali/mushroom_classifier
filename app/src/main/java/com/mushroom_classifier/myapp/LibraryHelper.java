package com.mushroom_classifier.myapp;

import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteException;

import android.content.Context;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.File;
import com.mushroom_classifier.myapp.CategoryInfo;

class LibraryHelper extends SQLiteOpenHelper {
    private final static int VERSION = 1;
    private static final String DATABASE_NAME = "fungi.db";
    private Context myContext;
    private SQLiteDatabase myDataBase;

    public LibraryHelper(Context context) throws SQLException {
        super(context, DATABASE_NAME, null, VERSION);
        myContext = context;
    }

    private void copyDataBase() throws IOException
    {

        InputStream mInput = myContext.getAssets().open(DATABASE_NAME);

        String outFileName = myContext.getFilesDir()+ DATABASE_NAME;
        OutputStream mOutput = new FileOutputStream(outFileName);
        byte[] mBuffer = new byte[2024];
        int mLength;
        while ((mLength = mInput.read(mBuffer)) > 0) {
            mOutput.write(mBuffer, 0, mLength);
        }
        mOutput.flush();
        mOutput.close();
        mInput.close();
    }

    public void createDatabase() throws IOException
    {

        boolean dbExist1 = checkDataBase();
        if(!dbExist1)
        {
            this.getReadableDatabase();
            try
            {
                this.close();
                copyDataBase();
            }
            catch (IOException e)
            {
                throw new Error("Error copying database");
            }
        }

    }
    public void db_delete()
    {
        File file = new File(myContext.getFilesDir() + DATABASE_NAME);
        if(file.exists())
        {
            file.delete();
            System.out.println("delete database file.");
        }
    }
    //Open database
    public void openDatabase() throws SQLException
    {
        try {
            copyDataBase();
        } catch (IOException e){

        }
        String myPath = myContext.getFilesDir() + DATABASE_NAME;
        myDataBase = SQLiteDatabase.openDatabase(myPath, null, SQLiteDatabase.OPEN_READWRITE);
    }

    public synchronized void closeDataBase()throws SQLException
    {
        if(myDataBase != null)
            myDataBase.close();
        super.close();
    }
    private boolean checkDataBase()
    {
        boolean checkDB = false;
        try
        {
            String myPath = myContext.getFilesDir() + DATABASE_NAME;
            File dbfile = new File(myPath);
            checkDB = dbfile.exists();
        }
        catch(SQLiteException e)
        {
        }
        return checkDB;
    }

    public void execSQL(String sql){
        // query = "UPDATE ..." or "DELETE... "
        try {
            //SQLiteDatabase myDataBase = getWritableDatabase();
            execSQLCmd(myDataBase,sql);
        }catch (SQLException e){
            e.printStackTrace();
            throw e;
        }
    }

    public CategoryInfo gainData(String cat_name){

        cat_name = cat_name.toLowerCase();
        String query = "SELECT id, name, info, example_img FROM fungi WHERE name='"+cat_name + "'";
        Cursor cursor = execQuery(query);
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
            // SQLiteDatabase db = getReadableDatabase();
            return myDataBase.rawQuery(query, null);
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
