package com.example.salesstysetgps.data.local;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Long;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class StopDao_Impl implements StopDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<StopEntity> __insertionAdapterOfStopEntity;

  private final SharedSQLiteStatement __preparedStmtOfUpdateName;

  private final SharedSQLiteStatement __preparedStmtOfClearAll;

  public StopDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfStopEntity = new EntityInsertionAdapter<StopEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `stops` (`id`,`name`,`locationLabel`,`address`,`phone`,`imageUri`,`lat`,`lng`,`startWallTimeMillis`,`endWallTimeMillis`,`durationElapsedMinutes`,`startElapsedRealtimeMillis`,`endElapsedRealtimeMillis`,`letter`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final StopEntity entity) {
        statement.bindLong(1, entity.getId());
        if (entity.getName() == null) {
          statement.bindNull(2);
        } else {
          statement.bindString(2, entity.getName());
        }
        if (entity.getLocationLabel() == null) {
          statement.bindNull(3);
        } else {
          statement.bindString(3, entity.getLocationLabel());
        }
        if (entity.getAddress() == null) {
          statement.bindNull(4);
        } else {
          statement.bindString(4, entity.getAddress());
        }
        if (entity.getPhone() == null) {
          statement.bindNull(5);
        } else {
          statement.bindString(5, entity.getPhone());
        }
        if (entity.getImageUri() == null) {
          statement.bindNull(6);
        } else {
          statement.bindString(6, entity.getImageUri());
        }
        statement.bindDouble(7, entity.getLat());
        statement.bindDouble(8, entity.getLng());
        statement.bindLong(9, entity.getStartWallTimeMillis());
        if (entity.getEndWallTimeMillis() == null) {
          statement.bindNull(10);
        } else {
          statement.bindLong(10, entity.getEndWallTimeMillis());
        }
        statement.bindLong(11, entity.getDurationElapsedMinutes());
        statement.bindLong(12, entity.getStartElapsedRealtimeMillis());
        if (entity.getEndElapsedRealtimeMillis() == null) {
          statement.bindNull(13);
        } else {
          statement.bindLong(13, entity.getEndElapsedRealtimeMillis());
        }
        if (entity.getLetter() == null) {
          statement.bindNull(14);
        } else {
          statement.bindString(14, entity.getLetter());
        }
      }
    };
    this.__preparedStmtOfUpdateName = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE stops SET name = ? WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfClearAll = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM stops";
        return _query;
      }
    };
  }

  @Override
  public Object upsert(final StopEntity stop, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfStopEntity.insert(stop);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object updateName(final long id, final String name,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfUpdateName.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, name);
        _argIndex = 2;
        _stmt.bindLong(_argIndex, id);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfUpdateName.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object clearAll(final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfClearAll.acquire();
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfClearAll.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<StopEntity>> observeStops() {
    final String _sql = "SELECT * FROM stops ORDER BY startWallTimeMillis ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"stops"}, new Callable<List<StopEntity>>() {
      @Override
      @NonNull
      public List<StopEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfLocationLabel = CursorUtil.getColumnIndexOrThrow(_cursor, "locationLabel");
          final int _cursorIndexOfAddress = CursorUtil.getColumnIndexOrThrow(_cursor, "address");
          final int _cursorIndexOfPhone = CursorUtil.getColumnIndexOrThrow(_cursor, "phone");
          final int _cursorIndexOfImageUri = CursorUtil.getColumnIndexOrThrow(_cursor, "imageUri");
          final int _cursorIndexOfLat = CursorUtil.getColumnIndexOrThrow(_cursor, "lat");
          final int _cursorIndexOfLng = CursorUtil.getColumnIndexOrThrow(_cursor, "lng");
          final int _cursorIndexOfStartWallTimeMillis = CursorUtil.getColumnIndexOrThrow(_cursor, "startWallTimeMillis");
          final int _cursorIndexOfEndWallTimeMillis = CursorUtil.getColumnIndexOrThrow(_cursor, "endWallTimeMillis");
          final int _cursorIndexOfDurationElapsedMinutes = CursorUtil.getColumnIndexOrThrow(_cursor, "durationElapsedMinutes");
          final int _cursorIndexOfStartElapsedRealtimeMillis = CursorUtil.getColumnIndexOrThrow(_cursor, "startElapsedRealtimeMillis");
          final int _cursorIndexOfEndElapsedRealtimeMillis = CursorUtil.getColumnIndexOrThrow(_cursor, "endElapsedRealtimeMillis");
          final int _cursorIndexOfLetter = CursorUtil.getColumnIndexOrThrow(_cursor, "letter");
          final List<StopEntity> _result = new ArrayList<StopEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final StopEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpName;
            if (_cursor.isNull(_cursorIndexOfName)) {
              _tmpName = null;
            } else {
              _tmpName = _cursor.getString(_cursorIndexOfName);
            }
            final String _tmpLocationLabel;
            if (_cursor.isNull(_cursorIndexOfLocationLabel)) {
              _tmpLocationLabel = null;
            } else {
              _tmpLocationLabel = _cursor.getString(_cursorIndexOfLocationLabel);
            }
            final String _tmpAddress;
            if (_cursor.isNull(_cursorIndexOfAddress)) {
              _tmpAddress = null;
            } else {
              _tmpAddress = _cursor.getString(_cursorIndexOfAddress);
            }
            final String _tmpPhone;
            if (_cursor.isNull(_cursorIndexOfPhone)) {
              _tmpPhone = null;
            } else {
              _tmpPhone = _cursor.getString(_cursorIndexOfPhone);
            }
            final String _tmpImageUri;
            if (_cursor.isNull(_cursorIndexOfImageUri)) {
              _tmpImageUri = null;
            } else {
              _tmpImageUri = _cursor.getString(_cursorIndexOfImageUri);
            }
            final double _tmpLat;
            _tmpLat = _cursor.getDouble(_cursorIndexOfLat);
            final double _tmpLng;
            _tmpLng = _cursor.getDouble(_cursorIndexOfLng);
            final long _tmpStartWallTimeMillis;
            _tmpStartWallTimeMillis = _cursor.getLong(_cursorIndexOfStartWallTimeMillis);
            final Long _tmpEndWallTimeMillis;
            if (_cursor.isNull(_cursorIndexOfEndWallTimeMillis)) {
              _tmpEndWallTimeMillis = null;
            } else {
              _tmpEndWallTimeMillis = _cursor.getLong(_cursorIndexOfEndWallTimeMillis);
            }
            final long _tmpDurationElapsedMinutes;
            _tmpDurationElapsedMinutes = _cursor.getLong(_cursorIndexOfDurationElapsedMinutes);
            final long _tmpStartElapsedRealtimeMillis;
            _tmpStartElapsedRealtimeMillis = _cursor.getLong(_cursorIndexOfStartElapsedRealtimeMillis);
            final Long _tmpEndElapsedRealtimeMillis;
            if (_cursor.isNull(_cursorIndexOfEndElapsedRealtimeMillis)) {
              _tmpEndElapsedRealtimeMillis = null;
            } else {
              _tmpEndElapsedRealtimeMillis = _cursor.getLong(_cursorIndexOfEndElapsedRealtimeMillis);
            }
            final String _tmpLetter;
            if (_cursor.isNull(_cursorIndexOfLetter)) {
              _tmpLetter = null;
            } else {
              _tmpLetter = _cursor.getString(_cursorIndexOfLetter);
            }
            _item = new StopEntity(_tmpId,_tmpName,_tmpLocationLabel,_tmpAddress,_tmpPhone,_tmpImageUri,_tmpLat,_tmpLng,_tmpStartWallTimeMillis,_tmpEndWallTimeMillis,_tmpDurationElapsedMinutes,_tmpStartElapsedRealtimeMillis,_tmpEndElapsedRealtimeMillis,_tmpLetter);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Object getStopsInTimeRange(final long routeStartTime, final long routeEndTime,
      final Continuation<? super List<StopEntity>> $completion) {
    final String _sql = "\n"
            + "    SELECT * FROM stops \n"
            + "    WHERE startWallTimeMillis >= ? \n"
            + "    AND (endWallTimeMillis <= ? OR endWallTimeMillis IS NULL)\n"
            + "    ORDER BY startWallTimeMillis ASC\n";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 2);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, routeStartTime);
    _argIndex = 2;
    _statement.bindLong(_argIndex, routeEndTime);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<StopEntity>>() {
      @Override
      @NonNull
      public List<StopEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfLocationLabel = CursorUtil.getColumnIndexOrThrow(_cursor, "locationLabel");
          final int _cursorIndexOfAddress = CursorUtil.getColumnIndexOrThrow(_cursor, "address");
          final int _cursorIndexOfPhone = CursorUtil.getColumnIndexOrThrow(_cursor, "phone");
          final int _cursorIndexOfImageUri = CursorUtil.getColumnIndexOrThrow(_cursor, "imageUri");
          final int _cursorIndexOfLat = CursorUtil.getColumnIndexOrThrow(_cursor, "lat");
          final int _cursorIndexOfLng = CursorUtil.getColumnIndexOrThrow(_cursor, "lng");
          final int _cursorIndexOfStartWallTimeMillis = CursorUtil.getColumnIndexOrThrow(_cursor, "startWallTimeMillis");
          final int _cursorIndexOfEndWallTimeMillis = CursorUtil.getColumnIndexOrThrow(_cursor, "endWallTimeMillis");
          final int _cursorIndexOfDurationElapsedMinutes = CursorUtil.getColumnIndexOrThrow(_cursor, "durationElapsedMinutes");
          final int _cursorIndexOfStartElapsedRealtimeMillis = CursorUtil.getColumnIndexOrThrow(_cursor, "startElapsedRealtimeMillis");
          final int _cursorIndexOfEndElapsedRealtimeMillis = CursorUtil.getColumnIndexOrThrow(_cursor, "endElapsedRealtimeMillis");
          final int _cursorIndexOfLetter = CursorUtil.getColumnIndexOrThrow(_cursor, "letter");
          final List<StopEntity> _result = new ArrayList<StopEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final StopEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpName;
            if (_cursor.isNull(_cursorIndexOfName)) {
              _tmpName = null;
            } else {
              _tmpName = _cursor.getString(_cursorIndexOfName);
            }
            final String _tmpLocationLabel;
            if (_cursor.isNull(_cursorIndexOfLocationLabel)) {
              _tmpLocationLabel = null;
            } else {
              _tmpLocationLabel = _cursor.getString(_cursorIndexOfLocationLabel);
            }
            final String _tmpAddress;
            if (_cursor.isNull(_cursorIndexOfAddress)) {
              _tmpAddress = null;
            } else {
              _tmpAddress = _cursor.getString(_cursorIndexOfAddress);
            }
            final String _tmpPhone;
            if (_cursor.isNull(_cursorIndexOfPhone)) {
              _tmpPhone = null;
            } else {
              _tmpPhone = _cursor.getString(_cursorIndexOfPhone);
            }
            final String _tmpImageUri;
            if (_cursor.isNull(_cursorIndexOfImageUri)) {
              _tmpImageUri = null;
            } else {
              _tmpImageUri = _cursor.getString(_cursorIndexOfImageUri);
            }
            final double _tmpLat;
            _tmpLat = _cursor.getDouble(_cursorIndexOfLat);
            final double _tmpLng;
            _tmpLng = _cursor.getDouble(_cursorIndexOfLng);
            final long _tmpStartWallTimeMillis;
            _tmpStartWallTimeMillis = _cursor.getLong(_cursorIndexOfStartWallTimeMillis);
            final Long _tmpEndWallTimeMillis;
            if (_cursor.isNull(_cursorIndexOfEndWallTimeMillis)) {
              _tmpEndWallTimeMillis = null;
            } else {
              _tmpEndWallTimeMillis = _cursor.getLong(_cursorIndexOfEndWallTimeMillis);
            }
            final long _tmpDurationElapsedMinutes;
            _tmpDurationElapsedMinutes = _cursor.getLong(_cursorIndexOfDurationElapsedMinutes);
            final long _tmpStartElapsedRealtimeMillis;
            _tmpStartElapsedRealtimeMillis = _cursor.getLong(_cursorIndexOfStartElapsedRealtimeMillis);
            final Long _tmpEndElapsedRealtimeMillis;
            if (_cursor.isNull(_cursorIndexOfEndElapsedRealtimeMillis)) {
              _tmpEndElapsedRealtimeMillis = null;
            } else {
              _tmpEndElapsedRealtimeMillis = _cursor.getLong(_cursorIndexOfEndElapsedRealtimeMillis);
            }
            final String _tmpLetter;
            if (_cursor.isNull(_cursorIndexOfLetter)) {
              _tmpLetter = null;
            } else {
              _tmpLetter = _cursor.getString(_cursorIndexOfLetter);
            }
            _item = new StopEntity(_tmpId,_tmpName,_tmpLocationLabel,_tmpAddress,_tmpPhone,_tmpImageUri,_tmpLat,_tmpLng,_tmpStartWallTimeMillis,_tmpEndWallTimeMillis,_tmpDurationElapsedMinutes,_tmpStartElapsedRealtimeMillis,_tmpEndElapsedRealtimeMillis,_tmpLetter);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object getStopById(final long stopId, final Continuation<? super StopEntity> $completion) {
    final String _sql = "SELECT * FROM stops WHERE id = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, stopId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<StopEntity>() {
      @Override
      @Nullable
      public StopEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfLocationLabel = CursorUtil.getColumnIndexOrThrow(_cursor, "locationLabel");
          final int _cursorIndexOfAddress = CursorUtil.getColumnIndexOrThrow(_cursor, "address");
          final int _cursorIndexOfPhone = CursorUtil.getColumnIndexOrThrow(_cursor, "phone");
          final int _cursorIndexOfImageUri = CursorUtil.getColumnIndexOrThrow(_cursor, "imageUri");
          final int _cursorIndexOfLat = CursorUtil.getColumnIndexOrThrow(_cursor, "lat");
          final int _cursorIndexOfLng = CursorUtil.getColumnIndexOrThrow(_cursor, "lng");
          final int _cursorIndexOfStartWallTimeMillis = CursorUtil.getColumnIndexOrThrow(_cursor, "startWallTimeMillis");
          final int _cursorIndexOfEndWallTimeMillis = CursorUtil.getColumnIndexOrThrow(_cursor, "endWallTimeMillis");
          final int _cursorIndexOfDurationElapsedMinutes = CursorUtil.getColumnIndexOrThrow(_cursor, "durationElapsedMinutes");
          final int _cursorIndexOfStartElapsedRealtimeMillis = CursorUtil.getColumnIndexOrThrow(_cursor, "startElapsedRealtimeMillis");
          final int _cursorIndexOfEndElapsedRealtimeMillis = CursorUtil.getColumnIndexOrThrow(_cursor, "endElapsedRealtimeMillis");
          final int _cursorIndexOfLetter = CursorUtil.getColumnIndexOrThrow(_cursor, "letter");
          final StopEntity _result;
          if (_cursor.moveToFirst()) {
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpName;
            if (_cursor.isNull(_cursorIndexOfName)) {
              _tmpName = null;
            } else {
              _tmpName = _cursor.getString(_cursorIndexOfName);
            }
            final String _tmpLocationLabel;
            if (_cursor.isNull(_cursorIndexOfLocationLabel)) {
              _tmpLocationLabel = null;
            } else {
              _tmpLocationLabel = _cursor.getString(_cursorIndexOfLocationLabel);
            }
            final String _tmpAddress;
            if (_cursor.isNull(_cursorIndexOfAddress)) {
              _tmpAddress = null;
            } else {
              _tmpAddress = _cursor.getString(_cursorIndexOfAddress);
            }
            final String _tmpPhone;
            if (_cursor.isNull(_cursorIndexOfPhone)) {
              _tmpPhone = null;
            } else {
              _tmpPhone = _cursor.getString(_cursorIndexOfPhone);
            }
            final String _tmpImageUri;
            if (_cursor.isNull(_cursorIndexOfImageUri)) {
              _tmpImageUri = null;
            } else {
              _tmpImageUri = _cursor.getString(_cursorIndexOfImageUri);
            }
            final double _tmpLat;
            _tmpLat = _cursor.getDouble(_cursorIndexOfLat);
            final double _tmpLng;
            _tmpLng = _cursor.getDouble(_cursorIndexOfLng);
            final long _tmpStartWallTimeMillis;
            _tmpStartWallTimeMillis = _cursor.getLong(_cursorIndexOfStartWallTimeMillis);
            final Long _tmpEndWallTimeMillis;
            if (_cursor.isNull(_cursorIndexOfEndWallTimeMillis)) {
              _tmpEndWallTimeMillis = null;
            } else {
              _tmpEndWallTimeMillis = _cursor.getLong(_cursorIndexOfEndWallTimeMillis);
            }
            final long _tmpDurationElapsedMinutes;
            _tmpDurationElapsedMinutes = _cursor.getLong(_cursorIndexOfDurationElapsedMinutes);
            final long _tmpStartElapsedRealtimeMillis;
            _tmpStartElapsedRealtimeMillis = _cursor.getLong(_cursorIndexOfStartElapsedRealtimeMillis);
            final Long _tmpEndElapsedRealtimeMillis;
            if (_cursor.isNull(_cursorIndexOfEndElapsedRealtimeMillis)) {
              _tmpEndElapsedRealtimeMillis = null;
            } else {
              _tmpEndElapsedRealtimeMillis = _cursor.getLong(_cursorIndexOfEndElapsedRealtimeMillis);
            }
            final String _tmpLetter;
            if (_cursor.isNull(_cursorIndexOfLetter)) {
              _tmpLetter = null;
            } else {
              _tmpLetter = _cursor.getString(_cursorIndexOfLetter);
            }
            _result = new StopEntity(_tmpId,_tmpName,_tmpLocationLabel,_tmpAddress,_tmpPhone,_tmpImageUri,_tmpLat,_tmpLng,_tmpStartWallTimeMillis,_tmpEndWallTimeMillis,_tmpDurationElapsedMinutes,_tmpStartElapsedRealtimeMillis,_tmpEndElapsedRealtimeMillis,_tmpLetter);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
