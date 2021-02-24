/*
 * Copyright 2019-2020 The Polypheny Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * This file incorporates code covered by the following terms:
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.polypheny.db.adapter.mongodb;


import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.apache.calcite.linq4j.AbstractEnumerable;
import org.apache.calcite.linq4j.Enumerable;
import org.apache.calcite.linq4j.Enumerator;
import org.apache.calcite.linq4j.Queryable;
import org.apache.calcite.linq4j.function.Function1;
import org.bson.BsonDocument;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.polypheny.db.adapter.DataContext;
import org.polypheny.db.adapter.java.AbstractQueryableTable;
import org.polypheny.db.plan.Convention;
import org.polypheny.db.plan.RelOptCluster;
import org.polypheny.db.plan.RelOptTable;
import org.polypheny.db.prepare.Prepare.CatalogReader;
import org.polypheny.db.rel.RelNode;
import org.polypheny.db.rel.core.TableModify;
import org.polypheny.db.rel.core.TableModify.Operation;
import org.polypheny.db.rel.logical.LogicalTableModify;
import org.polypheny.db.rel.type.RelDataType;
import org.polypheny.db.rel.type.RelDataTypeFactory;
import org.polypheny.db.rel.type.RelProtoDataType;
import org.polypheny.db.rex.RexNode;
import org.polypheny.db.schema.ModifiableTable;
import org.polypheny.db.schema.SchemaPlus;
import org.polypheny.db.schema.TranslatableTable;
import org.polypheny.db.schema.impl.AbstractTableQueryable;
import org.polypheny.db.util.Util;


/**
 * Table based on a MongoDB collection.
 */
public class MongoTable extends AbstractQueryableTable implements TranslatableTable, ModifiableTable {

    private final String collectionName;

    private final RelProtoDataType protoRowType;
    private MongoSchema mongoSchema;


    /**
     * Creates a MongoTable.
     */
    MongoTable( String collectionName, MongoSchema schema, RelProtoDataType proto ) {
        super( Object[].class );
        this.collectionName = collectionName;
        this.protoRowType = proto;
        this.mongoSchema = schema;
    }


    public String toString() {
        return "MongoTable {" + collectionName + "}";
    }


    @Override
    public RelDataType getRowType( RelDataTypeFactory typeFactory ) {
       /* final RelDataType mapType =
                typeFactory.createMapType(
                        typeFactory.createPolyType( PolyType.VARCHAR ),
                        typeFactory.createTypeWithNullability( typeFactory.createPolyType( PolyType.ANY ), true ) );
        */
        // TODO (PCP)
        // return typeFactory.builder().add( "_MAP", null, mapType ).build();
        return protoRowType.apply( typeFactory );
    }


    @Override
    public <T> Queryable<T> asQueryable( DataContext dataContext, SchemaPlus schema, String tableName ) {
        return new MongoQueryable<>( dataContext, schema, this, tableName );
    }


    @Override
    public RelNode toRel( RelOptTable.ToRelContext context, RelOptTable relOptTable ) {
        final RelOptCluster cluster = context.getCluster();
        return new MongoTableScan( cluster, cluster.traitSetOf( MongoRel.CONVENTION ), relOptTable, this, null );
    }


    /**
     * Executes a "find" operation on the underlying collection.
     *
     * For example,
     * <code>zipsTable.find("{state: 'OR'}", "{city: 1, zipcode: 1}")</code>
     *
     * @param mongoDb MongoDB connection
     * @param filterJson Filter JSON string, or null
     * @param projectJson Project JSON string, or null
     * @param fields List of fields to project; or null to return map
     * @return Enumerator of results
     */
    private Enumerable<Object> find( MongoDatabase mongoDb, String filterJson, String projectJson, List<Map.Entry<String, Class>> fields ) {
        final MongoCollection collection = mongoDb.getCollection( collectionName );
        final Bson filter = filterJson == null ? null : BsonDocument.parse( filterJson );
        final Bson project = projectJson == null ? null : BsonDocument.parse( projectJson );
        final Function1<Document, Object> getter = MongoEnumerator.getter( fields );
        return new AbstractEnumerable<Object>() {
            @Override
            public Enumerator<Object> enumerator() {
                @SuppressWarnings("unchecked") final FindIterable<Document> cursor = collection.find( filter ).projection( project );
                return new MongoEnumerator( cursor.iterator(), getter );
            }
        };
    }


    /**
     * Executes an "aggregate" operation on the underlying collection.
     *
     * For example:
     * <code>zipsTable.aggregate(
     * "{$filter: {state: 'OR'}",
     * "{$group: {_id: '$city', c: {$sum: 1}, p: {$sum: '$pop'}}}")
     * </code>
     *
     * @param mongoDb MongoDB connection
     * @param fields List of fields to project; or null to return map
     * @param operations One or more JSON strings
     * @return Enumerator of results
     */
    private Enumerable<Object> aggregate( final MongoDatabase mongoDb, final List<Map.Entry<String, Class>> fields, final List<String> operations ) {
        final List<Bson> list = new ArrayList<>();
        for ( String operation : operations ) {
            list.add( BsonDocument.parse( operation ) );
        }
        final Function1<Document, Object> getter = MongoEnumerator.getter( fields );
        return new AbstractEnumerable<Object>() {
            @Override
            public Enumerator<Object> enumerator() {
                final Iterator<Document> resultIterator;
                try {
                    resultIterator = mongoDb.getCollection( collectionName ).aggregate( list ).iterator();
                } catch ( Exception e ) {
                    throw new RuntimeException( "While running MongoDB query " + Util.toString( operations, "[", ",\n", "]" ), e );
                }
                return new MongoEnumerator( resultIterator, getter );
            }
        };
    }


    /**
     * Helper method to strip non-numerics from a string.
     *
     * Currently used to determine mongod versioning numbers
     * from buildInfo.versionArray for use in aggregate method logic.
     */
    private static Integer parseIntString( String valueString ) {
        return Integer.parseInt( valueString.replaceAll( "[^0-9]", "" ) );
    }


    @Override
    public Collection getModifiableCollection() {
        throw new RuntimeException( "getModifiableCollection() is not implemented for MongoDB adapter!" );
    }


    @Override
    public TableModify toModificationRel(
            RelOptCluster cluster,
            RelOptTable table,
            CatalogReader catalogReader,
            RelNode child,
            Operation operation,
            List<String> updateColumnList,
            List<RexNode> sourceExpressionList,
            boolean flattened ) {
        mongoSchema.getConvention().register( cluster.getPlanner() );
        return new LogicalTableModify(
                cluster,
                cluster.traitSetOf( Convention.NONE ),
                table,
                catalogReader,
                child,
                operation,
                updateColumnList,
                sourceExpressionList,
                flattened );
    }


    /**
     * Implementation of {@link org.apache.calcite.linq4j.Queryable} based on a {@link org.polypheny.db.adapter.mongodb.MongoTable}.
     *
     * @param <T> element type
     */
    public static class MongoQueryable<T> extends AbstractTableQueryable<T> {

        MongoQueryable( DataContext dataContext, SchemaPlus schema, MongoTable table, String tableName ) {
            super( dataContext, schema, table, tableName );
        }


        @Override
        public Enumerator<T> enumerator() {
            //noinspection unchecked
            final Enumerable<T> enumerable = (Enumerable<T>) getTable().find( getMongoDb(), null, null, null );
            return enumerable.enumerator();
        }


        private MongoDatabase getMongoDb() {
            return schema.unwrap( MongoSchema.class ).mongoDb;
        }


        private MongoTable getTable() {
            return (MongoTable) table;
        }


        /**
         * Called via code-generation.
         *
         * @see MongoMethod#MONGO_QUERYABLE_AGGREGATE
         */
        @SuppressWarnings("UnusedDeclaration")
        public Enumerable<Object> aggregate( List<Map.Entry<String, Class>> fields, List<String> operations ) {
            return getTable().aggregate( getMongoDb(), fields, operations );
        }


        /**
         * Called via code-generation.
         *
         * @param filterJson Filter document
         * @param projectJson Projection document
         * @param fields List of expected fields (and their types)
         * @return result of mongo query
         * @see MongoMethod#MONGO_QUERYABLE_FIND
         */
        @SuppressWarnings("UnusedDeclaration")
        public Enumerable<Object> find( String filterJson, String projectJson, List<Map.Entry<String, Class>> fields ) {
            return getTable().find( getMongoDb(), filterJson, projectJson, fields );
        }

    }

}
