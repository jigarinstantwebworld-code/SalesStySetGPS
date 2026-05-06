package com.example.salesstysetgps.data.local;

import androidx.annotation.NonNull;
import androidx.room.DatabaseConfiguration;
import androidx.room.InvalidationTracker;
import androidx.room.RoomDatabase;
import androidx.room.RoomOpenHelper;
import androidx.room.migration.AutoMigrationSpec;
import androidx.room.migration.Migration;
import androidx.room.util.DBUtil;
import androidx.room.util.TableInfo;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import java.lang.Class;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Generated;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class AppDatabase_Impl extends AppDatabase {
  private volatile StopDao _stopDao;

  private volatile RouteDao _routeDao;

  private volatile SyncDao _syncDao;

  @Override
  @NonNull
  protected SupportSQLiteOpenHelper createOpenHelper(@NonNull final DatabaseConfiguration config) {
    final SupportSQLiteOpenHelper.Callback _openCallback = new RoomOpenHelper(config, new RoomOpenHelper.Delegate(1) {
      @Override
      public void createAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `stops` (`id` INTEGER NOT NULL, `name` TEXT, `locationLabel` TEXT, `address` TEXT, `phone` TEXT, `imageUri` TEXT, `lat` REAL NOT NULL, `lng` REAL NOT NULL, `startWallTimeMillis` INTEGER NOT NULL, `endWallTimeMillis` INTEGER, `durationElapsedMinutes` INTEGER NOT NULL, `startElapsedRealtimeMillis` INTEGER NOT NULL, `endElapsedRealtimeMillis` INTEGER, `letter` TEXT, PRIMARY KEY(`id`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `route_points` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `routeId` INTEGER NOT NULL, `latitude` REAL NOT NULL, `longitude` REAL NOT NULL, `timestamp` INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `routes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `startTimeMillis` INTEGER NOT NULL, `endTimeMillis` INTEGER, `durationSeconds` INTEGER NOT NULL, `stopCount` INTEGER NOT NULL, `distanceMeters` REAL, `screenshotPath` TEXT)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `route_stop_relations` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `routeId` INTEGER NOT NULL, `stopId` INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `sync_data` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `apiName` TEXT NOT NULL, `lastSyncedTime` INTEGER NOT NULL, `status` INTEGER NOT NULL, `errorMessage` TEXT)");
        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");
        db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'b13744a56ec7c938395bc9c292f10d59')");
      }

      @Override
      public void dropAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS `stops`");
        db.execSQL("DROP TABLE IF EXISTS `route_points`");
        db.execSQL("DROP TABLE IF EXISTS `routes`");
        db.execSQL("DROP TABLE IF EXISTS `route_stop_relations`");
        db.execSQL("DROP TABLE IF EXISTS `sync_data`");
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onDestructiveMigration(db);
          }
        }
      }

      @Override
      public void onCreate(@NonNull final SupportSQLiteDatabase db) {
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onCreate(db);
          }
        }
      }

      @Override
      public void onOpen(@NonNull final SupportSQLiteDatabase db) {
        mDatabase = db;
        internalInitInvalidationTracker(db);
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onOpen(db);
          }
        }
      }

      @Override
      public void onPreMigrate(@NonNull final SupportSQLiteDatabase db) {
        DBUtil.dropFtsSyncTriggers(db);
      }

      @Override
      public void onPostMigrate(@NonNull final SupportSQLiteDatabase db) {
      }

      @Override
      @NonNull
      public RoomOpenHelper.ValidationResult onValidateSchema(
          @NonNull final SupportSQLiteDatabase db) {
        final HashMap<String, TableInfo.Column> _columnsStops = new HashMap<String, TableInfo.Column>(14);
        _columnsStops.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsStops.put("name", new TableInfo.Column("name", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsStops.put("locationLabel", new TableInfo.Column("locationLabel", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsStops.put("address", new TableInfo.Column("address", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsStops.put("phone", new TableInfo.Column("phone", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsStops.put("imageUri", new TableInfo.Column("imageUri", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsStops.put("lat", new TableInfo.Column("lat", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsStops.put("lng", new TableInfo.Column("lng", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsStops.put("startWallTimeMillis", new TableInfo.Column("startWallTimeMillis", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsStops.put("endWallTimeMillis", new TableInfo.Column("endWallTimeMillis", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsStops.put("durationElapsedMinutes", new TableInfo.Column("durationElapsedMinutes", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsStops.put("startElapsedRealtimeMillis", new TableInfo.Column("startElapsedRealtimeMillis", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsStops.put("endElapsedRealtimeMillis", new TableInfo.Column("endElapsedRealtimeMillis", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsStops.put("letter", new TableInfo.Column("letter", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysStops = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesStops = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoStops = new TableInfo("stops", _columnsStops, _foreignKeysStops, _indicesStops);
        final TableInfo _existingStops = TableInfo.read(db, "stops");
        if (!_infoStops.equals(_existingStops)) {
          return new RoomOpenHelper.ValidationResult(false, "stops(com.example.salesstysetgps.data.local.StopEntity).\n"
                  + " Expected:\n" + _infoStops + "\n"
                  + " Found:\n" + _existingStops);
        }
        final HashMap<String, TableInfo.Column> _columnsRoutePoints = new HashMap<String, TableInfo.Column>(5);
        _columnsRoutePoints.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsRoutePoints.put("routeId", new TableInfo.Column("routeId", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsRoutePoints.put("latitude", new TableInfo.Column("latitude", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsRoutePoints.put("longitude", new TableInfo.Column("longitude", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsRoutePoints.put("timestamp", new TableInfo.Column("timestamp", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysRoutePoints = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesRoutePoints = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoRoutePoints = new TableInfo("route_points", _columnsRoutePoints, _foreignKeysRoutePoints, _indicesRoutePoints);
        final TableInfo _existingRoutePoints = TableInfo.read(db, "route_points");
        if (!_infoRoutePoints.equals(_existingRoutePoints)) {
          return new RoomOpenHelper.ValidationResult(false, "route_points(com.example.salesstysetgps.data.local.RoutePointEntity).\n"
                  + " Expected:\n" + _infoRoutePoints + "\n"
                  + " Found:\n" + _existingRoutePoints);
        }
        final HashMap<String, TableInfo.Column> _columnsRoutes = new HashMap<String, TableInfo.Column>(7);
        _columnsRoutes.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsRoutes.put("startTimeMillis", new TableInfo.Column("startTimeMillis", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsRoutes.put("endTimeMillis", new TableInfo.Column("endTimeMillis", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsRoutes.put("durationSeconds", new TableInfo.Column("durationSeconds", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsRoutes.put("stopCount", new TableInfo.Column("stopCount", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsRoutes.put("distanceMeters", new TableInfo.Column("distanceMeters", "REAL", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsRoutes.put("screenshotPath", new TableInfo.Column("screenshotPath", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysRoutes = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesRoutes = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoRoutes = new TableInfo("routes", _columnsRoutes, _foreignKeysRoutes, _indicesRoutes);
        final TableInfo _existingRoutes = TableInfo.read(db, "routes");
        if (!_infoRoutes.equals(_existingRoutes)) {
          return new RoomOpenHelper.ValidationResult(false, "routes(com.example.salesstysetgps.data.local.RouteEntity).\n"
                  + " Expected:\n" + _infoRoutes + "\n"
                  + " Found:\n" + _existingRoutes);
        }
        final HashMap<String, TableInfo.Column> _columnsRouteStopRelations = new HashMap<String, TableInfo.Column>(3);
        _columnsRouteStopRelations.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsRouteStopRelations.put("routeId", new TableInfo.Column("routeId", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsRouteStopRelations.put("stopId", new TableInfo.Column("stopId", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysRouteStopRelations = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesRouteStopRelations = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoRouteStopRelations = new TableInfo("route_stop_relations", _columnsRouteStopRelations, _foreignKeysRouteStopRelations, _indicesRouteStopRelations);
        final TableInfo _existingRouteStopRelations = TableInfo.read(db, "route_stop_relations");
        if (!_infoRouteStopRelations.equals(_existingRouteStopRelations)) {
          return new RoomOpenHelper.ValidationResult(false, "route_stop_relations(com.example.salesstysetgps.data.local.RouteStopRelation).\n"
                  + " Expected:\n" + _infoRouteStopRelations + "\n"
                  + " Found:\n" + _existingRouteStopRelations);
        }
        final HashMap<String, TableInfo.Column> _columnsSyncData = new HashMap<String, TableInfo.Column>(5);
        _columnsSyncData.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSyncData.put("apiName", new TableInfo.Column("apiName", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSyncData.put("lastSyncedTime", new TableInfo.Column("lastSyncedTime", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSyncData.put("status", new TableInfo.Column("status", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSyncData.put("errorMessage", new TableInfo.Column("errorMessage", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysSyncData = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesSyncData = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoSyncData = new TableInfo("sync_data", _columnsSyncData, _foreignKeysSyncData, _indicesSyncData);
        final TableInfo _existingSyncData = TableInfo.read(db, "sync_data");
        if (!_infoSyncData.equals(_existingSyncData)) {
          return new RoomOpenHelper.ValidationResult(false, "sync_data(com.example.salesstysetgps.models.SyncDataEntity).\n"
                  + " Expected:\n" + _infoSyncData + "\n"
                  + " Found:\n" + _existingSyncData);
        }
        return new RoomOpenHelper.ValidationResult(true, null);
      }
    }, "b13744a56ec7c938395bc9c292f10d59", "2742d0007c70eb545e27cd98ce928823");
    final SupportSQLiteOpenHelper.Configuration _sqliteConfig = SupportSQLiteOpenHelper.Configuration.builder(config.context).name(config.name).callback(_openCallback).build();
    final SupportSQLiteOpenHelper _helper = config.sqliteOpenHelperFactory.create(_sqliteConfig);
    return _helper;
  }

  @Override
  @NonNull
  protected InvalidationTracker createInvalidationTracker() {
    final HashMap<String, String> _shadowTablesMap = new HashMap<String, String>(0);
    final HashMap<String, Set<String>> _viewTables = new HashMap<String, Set<String>>(0);
    return new InvalidationTracker(this, _shadowTablesMap, _viewTables, "stops","route_points","routes","route_stop_relations","sync_data");
  }

  @Override
  public void clearAllTables() {
    super.assertNotMainThread();
    final SupportSQLiteDatabase _db = super.getOpenHelper().getWritableDatabase();
    try {
      super.beginTransaction();
      _db.execSQL("DELETE FROM `stops`");
      _db.execSQL("DELETE FROM `route_points`");
      _db.execSQL("DELETE FROM `routes`");
      _db.execSQL("DELETE FROM `route_stop_relations`");
      _db.execSQL("DELETE FROM `sync_data`");
      super.setTransactionSuccessful();
    } finally {
      super.endTransaction();
      _db.query("PRAGMA wal_checkpoint(FULL)").close();
      if (!_db.inTransaction()) {
        _db.execSQL("VACUUM");
      }
    }
  }

  @Override
  @NonNull
  protected Map<Class<?>, List<Class<?>>> getRequiredTypeConverters() {
    final HashMap<Class<?>, List<Class<?>>> _typeConvertersMap = new HashMap<Class<?>, List<Class<?>>>();
    _typeConvertersMap.put(StopDao.class, StopDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(RouteDao.class, RouteDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(SyncDao.class, SyncDao_Impl.getRequiredConverters());
    return _typeConvertersMap;
  }

  @Override
  @NonNull
  public Set<Class<? extends AutoMigrationSpec>> getRequiredAutoMigrationSpecs() {
    final HashSet<Class<? extends AutoMigrationSpec>> _autoMigrationSpecsSet = new HashSet<Class<? extends AutoMigrationSpec>>();
    return _autoMigrationSpecsSet;
  }

  @Override
  @NonNull
  public List<Migration> getAutoMigrations(
      @NonNull final Map<Class<? extends AutoMigrationSpec>, AutoMigrationSpec> autoMigrationSpecs) {
    final List<Migration> _autoMigrations = new ArrayList<Migration>();
    return _autoMigrations;
  }

  @Override
  public StopDao stopDao() {
    if (_stopDao != null) {
      return _stopDao;
    } else {
      synchronized(this) {
        if(_stopDao == null) {
          _stopDao = new StopDao_Impl(this);
        }
        return _stopDao;
      }
    }
  }

  @Override
  public RouteDao routePointDao() {
    if (_routeDao != null) {
      return _routeDao;
    } else {
      synchronized(this) {
        if(_routeDao == null) {
          _routeDao = new RouteDao_Impl(this);
        }
        return _routeDao;
      }
    }
  }

  @Override
  public SyncDao syncDao() {
    if (_syncDao != null) {
      return _syncDao;
    } else {
      synchronized(this) {
        if(_syncDao == null) {
          _syncDao = new SyncDao_Impl(this);
        }
        return _syncDao;
      }
    }
  }
}
