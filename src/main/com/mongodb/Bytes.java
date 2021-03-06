// Bytes.java

/**
 *      Copyright (C) 2008 10gen Inc.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package com.mongodb;

import java.nio.*;
import java.nio.charset.*;
import java.util.regex.Pattern;
import java.util.*;
import java.util.logging.*;

import org.bson.*;
import org.bson.types.*;

/**
 * Class that hold definitions of the wire protocol
 * @author antoine
 */
public class Bytes extends BSON {
    
    static final Logger LOGGER = Logger.getLogger( "com.mongodb" );
    
    static final boolean D = Boolean.getBoolean( "DEBUG.MONGO" );

    static {
        if ( LOGGER.getLevel() == null ){
            if ( D )
                LOGGER.setLevel( Level.ALL );
            else
                LOGGER.setLevel( Level.WARNING );
        }
    }

    /** Little-endian */
    public static final ByteOrder ORDER = ByteOrder.LITTLE_ENDIAN;

    /** this size is used to prevent insertion of objects that are too large for db */
    static final int MAX_OBJECT_SIZE = 1024 * 1024 * 32;

    /** target size of an insert batch */
    static final int BATCH_INSERT_SIZE = 1024 * 1024 * 16;
    
    static final int CONNECTIONS_PER_HOST = Integer.parseInt( System.getProperty( "MONGO.POOLSIZE" , "10" ) );


    // --- network protocol options

    /**
     * Tailable means cursor is not closed when the last data is retrieved.
     * Rather, the cursor marks the final object's position.
     * You can resume using the cursor later, from where it was located, if more data were received.
     * Like any "latent cursor", the cursor may become invalid at some point (CursorNotFound) – for example if the final object it references were deleted.
     */
    public static final int QUERYOPTION_TAILABLE = 1 << 1;
    /**
     * Allow query of replica slave. Normally these return an error except for namespace "local".
     */
    public static final int QUERYOPTION_SLAVEOK = 1 << 2;
    /**
     * Internal replication use only - driver should not set
     */
    public static final int QUERYOPTION_OPLOGREPLAY = 1 << 3;
    /**
     * The server normally times out idle cursors after an inactivity period (10 minutes) to prevent excess memory use.
     * Set this option to prevent that.
     */
    public static final int QUERYOPTION_NOTIMEOUT = 1 << 4;
    /**
     * Use with TailableCursor.
     * If we are at the end of the data, block for a while rather than returning no data.
     * After a timeout period, we do return as normal.
     */
    public static final int QUERYOPTION_AWAITDATA = 1 << 5;
    /**
     * Stream the data down full blast in multiple "more" packages, on the assumption that the client will fully read all data queried.
     * Faster when you are pulling a lot of data and know you want to pull it all down.
     * Note: the client is not allowed to not read all the data unless it closes the connection.
     */
    public static final int QUERYOPTION_EXHAUST = 1 << 6;

    /**
     * Set when getMore is called but the cursor id is not valid at the server.
     * Returned with zero results. 
     */
    public static final int RESULTFLAG_CURSORNOTFOUND = 1;
    /**
     * Set when query failed.
     * Results consist of one document containing an "$err" field describing the failure.
     */
    public static final int RESULTFLAG_ERRSET = 2;
    /**
     * Drivers should ignore this.
     * Only mongos will ever see this set, in which case, it needs to update config from the server.
     */
    public static final int RESULTFLAG_SHARDCONFIGSTALE = 4;
    /**
     * Set when the server supports the AwaitData Query option.
     * If it doesn't, a client should sleep a little between getMore's of a Tailable cursor.
     * Mongod version 1.6 supports AwaitData and thus always sets AwaitCapable.
     */
    public static final int RESULTFLAG_AWAITCAPABLE = 8;

    static class OptionHolder {
        OptionHolder( OptionHolder parent ){
            _parent = parent;
        }

        void set( int options ){
            _options = options;
            _hasOptions = true;
        }
        
        int get(){
            if ( _hasOptions )
                return _options;
            if ( _parent == null )
                return 0;
            return _parent.get();
        }
        
        void add( int option ){
            set( get() | option );
        }

        void reset(){
            _hasOptions = false;
        }

        final OptionHolder _parent;
        
        int _options = 0;
        boolean _hasOptions = false;
    }

    /**
     * Gets the type byte for a given object.
     * @param o the object
     * @return the byte value associated with the type, or 0 if <code>o</code> was <code>null</code>
     */
    @SuppressWarnings("deprecation")
    public static byte getType( Object o ){
        if ( o == null )
            return NULL;

        if ( o instanceof DBPointer )
            return REF;

        if ( o instanceof Number )
            return NUMBER;
        
        if ( o instanceof String )
            return STRING;
        
        if ( o instanceof java.util.List )
            return ARRAY;

        if ( o instanceof byte[] )
            return BINARY;

        if ( o instanceof ObjectId )
            return OID;
        
        if ( o instanceof Boolean )
            return BOOLEAN;
        
        if ( o instanceof java.util.Date )
            return DATE;

        if ( o instanceof java.util.regex.Pattern )
            return REGEX;
        
        if ( o instanceof DBObject )
            return OBJECT;

        return 0;
    }

    static final ObjectId COLLECTION_REF_ID = new ObjectId( -1 , -1 , -1 );
}
