/*
 * Copyright 2010-2014 Jamling(li.jamling@gmail.com).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cn.ieclipse.aorm;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import cn.ieclipse.aorm.annotation.ColumnWrap;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;

/**
 * The session of communication with database. Provided all the GRUD operation
 * to databases.
 * 
 * @author Jamling
 */
public class Session {
    private SQLiteOpenHelper mHelper;
    private ContentResolver mResolver;

    private void log(String msg) {
        Aorm.logv(msg);
    }

    public Session(SQLiteOpenHelper helper) {
        mHelper = helper;
    }

    /**
     * Initialize the session.
     * 
     * @param helper
     *            A helper object to create, open, and/or manage a database. See
     *            {@link android.database.sqlite.SQLiteOpenHelper
     *            SQLiteOpenHelper}
     * @param resolver
     *            The {@linkplain android.content.ContentResolver
     *            ContentResolver} instance for your application's package.
     * 
     */
    public Session(SQLiteOpenHelper helper, ContentResolver resolver) {
        mHelper = helper;
        mResolver = resolver;
    }

    protected long insert(String table, String nullColumnHack,
            ContentValues values) {
        long id = mHelper.getWritableDatabase().insert(table, nullColumnHack,
                values);
        log("insert rowID : " + id);
        return id;
    }

    protected int update(String table, ContentValues values, String where,
            String[] args) {
        int count = mHelper.getWritableDatabase().update(table, values, where,
                args);
        log("update counts : " + count);
        return count;
    }

    protected int delete(String table, String where, String[] args) {
        int count = mHelper.getWritableDatabase().delete(table, where, args);
        log("delete counts : " + count);
        return count;
    }

    protected Cursor rawQuery(String sql, String[] args) {
        return mHelper.getReadableDatabase().rawQuery(sql, args);
    }

    protected Cursor query(String table, String[] columns, String where,
            int limit) {
        return mHelper.getReadableDatabase().query(table, columns, where, null,
                null, null, null, String.valueOf(limit));
    }

    protected void execSQL(String sql) {
        mHelper.getWritableDatabase().execSQL(sql);
    }

    protected void execSQL(String sql, Object[] args) {
        mHelper.getWritableDatabase().execSQL(sql, args);
    }

    public void beginTransaction() {
        mHelper.getWritableDatabase().beginTransaction();
    }

    public void endTransaction() {
        mHelper.getWritableDatabase().endTransaction();
    }

    /**
     * Insert the object as a row into your database
     * 
     * @param obj
     *            the object instance
     * @param nullColumnHack
     *            optional; may be null. SQL doesn't allow inserting a
     *            completely empty row without naming at least one column name.
     *            If your provided values is empty, no column names are known
     *            and an empty row can't be inserted. If not set to null, the
     *            nullColumnHack parameter provides the name of nullable column
     *            name to explicitly insert a NULL into in the case where your
     *            values is empty.
     * 
     * @return the row ID of the newly inserted row, or -1 if an error occurred
     */
    public long insert(Object obj, String nullColumnHack) {
        Row row = new Row(obj);
        ContentValues values = row.getContentValues();
        log("insert " + row.table + " values: " + values);
        long id = insert(row.table, nullColumnHack, values);

        notifySessionListener(obj.getClass());
        return id;
    }

    /**
     * Insert the object as a row into your database
     * 
     * @param obj
     *            the object instance
     * 
     * @return the row ID of the newly inserted row, or -1 if an error occurred
     */
    public long insert(Object obj) {
        return insert(obj, null);
    }

    /**
     * Insert the object as a row into your database with a native SQL.
     * 
     * @param obj
     *            the object instance
     */
    public void insertNative(Object obj) {
        Row row = new Row(obj);

        StringBuilder sb = new StringBuilder();
        sb.append("INSERT INTO ");
        sb.append(row.table);
        sb.append(" (");

        StringBuilder sb2 = new StringBuilder();
        int size = row.colNames.size();
        String colName;
        for (int i = 0; i < size; i++) {
            colName = row.colNames.get(i);
            sb.append(colName);
            sb2.append('?');
            if (i + 1 < size) {
                sb.append(",");
                sb2.append(',');
            }
        }

        sb.append(") VALUES (");
        sb.append(sb2);
        sb.append(")");
        String sql = sb.toString();
        log("insertNative sql: " + sql + "; args: " + row.args);
        execSQL(sql, row.getArgsArray());
        notifySessionListener(obj.getClass());
    }

    /**
     * Insert or update the object to database. If your object PK value more
     * than 0, will execute the update, otherwise insert the object to database.
     * 
     * @see #insertOrUpdate(Object, String)
     * @param obj
     *            the object instance
     * @return the row ID of the newly inserted row or the number of rows
     *         affected when updated
     */
    public long insertOrUpdate(Object obj) {
        return insert(obj, null);
    }

    /**
     * Insert or update the object to database. If your object PK value more
     * than 0, will execute the update, otherwise insert the object to database.
     * 
     * @see #insert(Object, String)
     * @see #update(Object)
     * @param obj
     *            the object instance
     * @param nullColumnHack
     * @return the row ID of the newly inserted row or the number of rows
     *         affected when updated
     */
    public long insertOrUpdate(Object obj, String nullColumnHack) {
        long ret = -1;
        Row row = new Row(obj);
        ContentValues values = row.getContentValues();

        long pkLong = row.getId();
        boolean update = Aorm.getExactInsertOrUpdate() ? get(obj) != null
                : pkLong > 0;
        String str = update ? "update" : "insert";
        log("insertOrUpdate(" + str + ") " + row.table + " values: " + values);
        if (update) {
            ret = update(row.table, values, row.pk + "=?",
                    new String[] { String.valueOf(row.pkValue) });
        } else {
            ret = insert(row.table, nullColumnHack, values);
        }
        notifySessionListener(obj.getClass());
        return ret;
    }

    /**
     * Update the object to database.
     * 
     * @param obj
     *            the object instance
     * @return the number of rows affected
     */
    public int update(Object obj) {
        Row row = new Row(obj);
        ContentValues values = row.getContentValues();
        String where = row.pk + "=" + row.pkValue;
        log("update " + row.table + " values: " + values);
        int count = update(row.table, values, where, null);
        notifySessionListener(obj.getClass());
        return count;
    }

    /**
     * Update from database with criteria.
     * 
     * @param criteria
     *            the criteria query instance.
     * @param values
     *            new values to update. The key of ContentValues is java
     *            property name, if the criteria has an alias, the key must add
     *            a $Alias. prefix.
     * @return the number of rows affected
     */
    public int update(Criteria criteria, ContentValues values) {
        String table = Mapping.getInstance().getTableName(
                criteria.getRoot().getClazz());
        String sql = criteria.toSQL();
        String where = criteria.getWhere();
        String[] args = criteria.getStringArgs();
        ContentValues colValues = new ContentValues(values.size());
        for (String key : values.keySet()) {
            Row.putColumnValues(colValues, criteria.property2Column(key),
                    values.get(key));
        }
        log("update " + table + " values: " + colValues + ", where = " + where
                + ", args = " + criteria.getArgs());
        int count = update(table, colValues, where, args);
        notifySessionListener(criteria.getRoot().getClazz());
        return count;
    }

    @Deprecated
    public void updateNative(Object obj) {
        Row row = new Row(obj);
        StringBuilder sb = new StringBuilder();
        sb.append("UPDATE ");
        sb.append(row.table);
        sb.append(" SET ");
        String pk = row.pk;
        Object pkValue = row.pkValue;
        if (pk == null) {
            // throw new ORMException("ORM Error: no primary key found in "
            // + obj.getClass());
        }

        int size = row.colNames.size();
        String current;
        for (int i = 0; i < size; i++) {
            current = row.colNames.get(i);
            sb.append(current);
            sb.append('=');
            sb.append('?');
            if (i + 1 < size) {
                sb.append(",");
            }
        }
        if (pk != null) {
            sb.append(" WHERE ");
            sb.append(pk);
            sb.append('=');
            sb.append(pkValue);
        }

        String sql = sb.toString();
        log("updateNative sql: " + sql + " ,args:" + row.args);
        execSQL(sql, row.getArgsArray());
        notifySessionListener(obj.getClass());
    }

    /**
     * Delete the object from database
     * 
     * @see #deleteById(Class, long)
     * @param obj
     *            the object instance
     * @return the number of rows deleted
     */
    public int delete(Object obj) {
        long id = getPkValue(obj);
        int count = deleteById(obj.getClass(), id);
        return count;
    }

    /**
     * Delete a row from database by PK
     * 
     * @param clazz
     *            the object class
     * @param id
     *            the PK value of object
     * @return the number of rows deleted
     */
    public int deleteById(Class<?> clazz, long id) {
        String table = Mapping.getInstance().getTableName(clazz);
        String pk = Mapping.getInstance().getPK(clazz);
        String where = pk + "=" + id;
        log("deleteById " + table + " where: " + where);
        int count = delete(table, where, null);
        notifySessionListener(clazz);
        return count;
    }

    /**
     * Delete a row from database by PK with native SQL.
     * 
     * @param clazz
     *            the object class
     * @param id
     *            the PK value of object
     */
    public void deleteByIdNative(Class<?> clazz, long id) {
        String table = Mapping.getInstance().getTableName(clazz);
        String pk = Mapping.getInstance().getPK(clazz);
        StringBuilder sb = new StringBuilder();
        sb.append("DELETE FROM ");
        sb.append(table);
        sb.append(" WHERE ");
        sb.append(pk);
        sb.append('=');
        sb.append(id);
        String sql = sb.toString();
        log("deleteByIdNative sql: " + sql);
        execSQL(sql);
        notifySessionListener(clazz);
    }

    /**
     * Delete all rows of database
     * 
     * @param clazz
     *            the class mapping to table in database.
     */
    public void deleteAll(Class<?> clazz) {
        String table = Mapping.getInstance().getTableName(clazz);
        if (table != null) {
            StringBuilder sb = new StringBuilder();
            sb.append("DELETE FROM ");
            sb.append(table);
            String sql = sb.toString();
            log("deleteAll sql: " + sql);
            execSQL(sql);
            notifySessionListener(clazz);
        }
    }

    /**
     * Update from database with criteria.
     * 
     * @param criteria
     *            the criteria query instance.
     * 
     * @return the number of rows deleted
     */
    public int delete(Criteria criteria) {
        String sql = criteria.toSQL();
        String table = Mapping.getInstance().getTableName(
                criteria.getRoot().getClazz());
        log("delete " + table + " where: " + criteria.getWhere());
        int count = delete(table, criteria.getWhere(), criteria.getStringArgs());
        notifySessionListener(criteria.getRoot().getClass());
        return count;
    }

    /**
     * Query from database with criteria. with not notify a change to URI. Same
     * as query(Criteria, null)
     * 
     * @see #query(Criteria, Uri)
     * @param criteria
     *            the criteria query instance.
     * @return the cursor of result.
     */
    public Cursor query(Criteria criteria) {
        return query(criteria, null);
    }

    /**
     * Query from database with criteria. with a change notify to URI.
     * 
     * @param criteria
     *            the criteria query instance.
     * @param uri
     *            the notify Uri.
     * @return the cursor of result.
     */
    public Cursor query(Criteria criteria, Uri uri) {
        String sql = criteria.toSQL();
        log("query sql: " + sql);
        Cursor c = rawQuery(sql, criteria.getStringArgs());
        if (uri != null && mResolver != null && c != null) {
            c.setNotificationUri(mResolver, uri);
        }
        return c;
    }

    /**
     * Count the {@link Criteria} query number.
     * 
     * @param criteria
     *            the criteria query instance.
     * 
     * @return the number of rows deleted
     */
    public int count(Criteria criteria) {
        String sql = criteria.toSQL();
        String sql2 = "SELECT COUNT(_id) " + sql.substring(sql.indexOf("FROM"));
        log("count sql: " + sql2);
        Cursor c = rawQuery(sql2, criteria.getStringArgs());
        if (c != null) {
            if (c.moveToFirst()) {
                return c.getInt(0);
            }
            c.close();
        }
        return 0;
    }

    /**
     * Use sum() function of database
     * 
     * @param criteria
     *            the criteria query instance.
     * 
     * @param property
     *            the java property to calculate sum.
     * @return the result of sum() function in database.
     */
    public int sum(Criteria criteria, String property) {
        String sql = criteria.toSQL();
        String column = criteria.property2Column(property);
        String sql2 = "SELECT SUM(" + column + ") "
                + sql.substring(sql.indexOf("FROM"));
        log("sum sql: " + sql2);
        Cursor c = rawQuery(sql2, criteria.getStringArgs());
        if (c != null) {
            if (c.moveToFirst()) {
                return c.getInt(0);
            }
            c.close();
        }
        return 0;
    }

    /**
     * Query all records of a table in database, and convert to objects list.
     * 
     * @param clazz
     *            the mapping table class
     * @return converted objects list
     */
    public <T> List<T> list(Class<T> clazz) {
        Cursor c = query(Criteria.create(clazz));
        return CursorUtils.getFromCursor(c, clazz, null);
    }

    /**
     * Query with a {@link Criteria} and convert to objects list.
     * 
     * <pre>
     * Criteria criteria = Criteria.create(A.class);
     * criteria.addChild(B.class, &quot;b&quot;);
     * criteria.addChild(C.class, &quot;c&quot;);
     * List&lt;A&gt; list = session.list(criteria);
     * for (A a : list) {
     *     System.out.println(a);
     * }
     * </pre>
     * 
     * @param criteria
     *            the criteria query instance.
     * @return converted objects list
     */
    public List list(Criteria criteria) {
        Cursor c = query(criteria);
        return CursorUtils.getFromCursor(c, criteria);
    }

    /**
     * Query with a {@link Criteria} and convert to objects list. All the query
     * projections will mapping to root or child {@link Criteria}, example:
     * 
     * <pre>
     * Criteria criteria = Criteria.create(A.class);
     * criteria.addChild(B.class, &quot;b&quot;);
     * criteria.addChild(C.class, &quot;c&quot;);
     * List&lt;Object[]&gt; list = session.listAll(criteria);
     * for (Object[] obj : list) {
     *     A a = obj[0];
     *     B b = obj[1];
     *     C c = obj[2];
     * }
     * </pre>
     * 
     * @param criteria
     *            the criteria query instance.
     * @return converted objects list
     */
    public List<Object[]> listAll(Criteria criteria) {
        Cursor c = query(criteria);
        return CursorUtils.getFromCursor(c, criteria.getProjectionClass(),
                criteria.getProjectionSeparators());
    }

    /**
     * Query with a {@link Criteria} and convert the first result record to
     * object.
     * 
     * @param criteria
     *            the criteria query instance.
     * @return converted object
     */
    public Object get(Criteria criteria) {
        List list = list(criteria);
        if (!list.isEmpty()) {
            return list.get(0);
        }
        return null;
    }

    /**
     * Query the database by primary key, and convert the result to object.
     * 
     * @param clazz
     *            the mapping table class
     * @param id
     *            the value of primary key
     * @return clazz instance or null if the record not exists
     */
    public <T> T get(Class<T> clazz, long id) {
        String table = Mapping.getInstance().getTableName(clazz);
        List<ColumnWrap> temp = Mapping.getInstance().getColumns(clazz);
        String[] columns = new String[temp.size()];
        int i = 0;
        for (ColumnWrap c : temp) {
            columns[i++] = c.getColumnName();
        }
        String pk = Mapping.getInstance().getPK(clazz);
        // Criteria criteria = Criteria.create(clazz).add(Restrictions.eq(pk,
        // id));
        Cursor c = query(table, columns, pk + "=" + id, 1);// query(criteria);

        List<T> list = CursorUtils.getFromCursor(c, clazz, null);
        if (!list.isEmpty()) {
            return list.get(0);
        }

        return null;
    }

    /**
     * Query the database by primary key, and convert the result to object.
     * 
     * @param obj
     *            the object has been set the primary key value.
     * 
     * @return the full object or null if the record not exists
     */
    public <T> T get(T obj) {
        // String table = Cache.getInstance().getTableName(obj.getClass());
        String pk = Mapping.getInstance().getPKProperty(obj.getClass());
        long id = getPkValue(obj);
        Criteria criteria = Criteria.create(obj.getClass()).add(
                Restrictions.eq(pk, id));
        Cursor c = query(criteria);
        @SuppressWarnings("unchecked")
        List<T> list = CursorUtils.getFromCursor(c, (Class<T>) obj.getClass(),
                null);
        if (!list.isEmpty()) {
            return list.get(0);
        }
        return null;
    }

    private long getPkValue(Object obj) {
        String pk = Mapping.getInstance().getPK(obj.getClass());
        long id = 0;
        Object pkValue = null;
        Method getter = Mapping.getInstance().getGetterByColumn(pk,
                obj.getClass());
        try {
            pkValue = getter.invoke(obj, (Object[]) null);
            id = Long.parseLong(pkValue.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return id;
    }

    private static class Row {
        String table;
        String pk;
        Object pkValue;
        Class<?> clz;
        ArrayList<Object> args = new ArrayList<Object>();
        ArrayList<String> colNames = new ArrayList<String>();

        public Row(Object obj) {
            clz = obj.getClass();
            table = Mapping.getInstance().getTableName(clz);
            pk = Mapping.getInstance().getPK(clz);
            List<ColumnWrap> list = Mapping.getInstance().getColumns(clz);
            int size = list.size();
            ColumnWrap current;
            String propName;
            String colName;
            Object colValue;
            for (int i = 0; i < size; i++) {
                current = list.get(i);
                try {
                    propName = current.getPropertyName();
                    colName = current.getColumnName();
                    // Method getter = CursorUtils
                    // .getObjGetter(colName, clz, null);
                    Method getter = getMethod(clz, current);
                    colValue = getter.invoke(obj, (Object[]) null);
                    if (colValue != null) {
                        if (pk.equals(colName)) {
                            pkValue = colValue;
                        } else {
                            args.add(colValue);
                            colNames.add(colName);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    throw new ORMException(e);
                }
            }
        }

        Object[] getArgsArray() {
            return args.toArray(new Object[args.size()]);
        }

        private Method getMethod(Class<?> clz, ColumnWrap col) throws Exception {
            String getter = col.getGetter();
            return clz.getDeclaredMethod(getter, (Class<?>[]) null);
        }

        long getId() {
            long id = 0;
            try {
                id = Long.parseLong(pkValue.toString());
            } catch (Exception e) {
                e.printStackTrace();
            }
            return id;
        }

        ContentValues getContentValues() {
            ContentValues values = new ContentValues();
            int size = colNames.size();
            String colName;
            Object colValue;
            for (int i = 0; i < size; i++) {
                colName = colNames.get(i);
                colValue = args.get(i);
                putColumnValues(values, colName, colValue);
            }
            return values;
        }

        static void putColumnValues(ContentValues colValues, String colName,
                Object colValue) {
            Class<?> colClass = colValue.getClass();
            if (int.class == colClass || Integer.class == colClass) {
                colValues.put(colName, (Integer) colValue);
            } else if (short.class == colClass || Short.class == colClass) {
                colValues.put(colName, (Short) colValue);
            } else if (long.class == colClass || Long.class == colClass) {
                colValues.put(colName, (Long) colValue);
            } else if (byte.class == colClass || Byte.class == colClass) {
                colValues.put(colName, (Byte) colValue);
            } else if (float.class == colClass || Float.class == colClass) {
                colValues.put(colName, (Float) colValue);
            } else if (double.class == colClass || Double.class == colClass) {
                colValues.put(colName, (Double) colValue);
            } else if (byte[].class == colClass || Byte[].class == colClass) {
                colValues.put(colName, (byte[]) colValue);
            } else if (String.class == colClass) {
                colValues.put(colName, (String) colValue);
            }
        }
    }

    private SessionObserver observer;

    public void registerObserver(Uri uri) {
        if (observer == null) {
            observer = new SessionObserver(null, this);
        }
        mResolver.registerContentObserver(uri, true, observer);
    }

    public void unregisterObserver() {
        if (observer != null) {
            mResolver.unregisterContentObserver(observer);
        }
    }

    public void onChange(boolean selfChange) {
        notifySessionListener(null);
    }

    private Set<SessionListener> listeners = null;

    public void addSessionListener(SessionListener listener) {
        if (listeners == null) {
            listeners = new HashSet<SessionListener>();
        }
        synchronized (listener) {
            listeners.add(listener);
        }
    }

    public void removeSessionListener(SessionListener listener) {
        if (listeners != null) {
            synchronized (listeners) {
                listeners.remove(listener);
            }
        }
    }

    private void notifySessionListener(Class<?> clazz) {
        if (listeners != null) {
            synchronized (listeners) {
                for (SessionListener l : listeners) {
                    l.onChange(clazz);
                }
            }
        }
    }

    public static interface SessionListener {
        void onChange(Class<?> clazz);
    }
}
