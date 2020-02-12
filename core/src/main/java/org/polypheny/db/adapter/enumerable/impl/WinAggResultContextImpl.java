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

package org.polypheny.db.adapter.enumerable.impl;


import org.polypheny.db.adapter.enumerable.RexToLixTranslator;
import org.polypheny.db.adapter.enumerable.WinAggFrameResultContext;
import org.polypheny.db.adapter.enumerable.WinAggImplementor;
import org.polypheny.db.adapter.enumerable.WinAggResultContext;
import java.util.List;
import java.util.function.Function;
import org.apache.calcite.linq4j.tree.BlockBuilder;
import org.apache.calcite.linq4j.tree.Expression;


/**
 * Implementation of {@link org.polypheny.db.adapter.enumerable.WinAggResultContext}.
 */
public abstract class WinAggResultContextImpl extends AggResultContextImpl implements WinAggResultContext {

    private final Function<BlockBuilder, WinAggFrameResultContext> frame;


    /**
     * Creates window aggregate result context.
     *
     * @param block code block that will contain the added initialization
     * @param accumulator accumulator variables that store the intermediate aggregate state
     */
    public WinAggResultContextImpl( BlockBuilder block, List<Expression> accumulator, Function<BlockBuilder, WinAggFrameResultContext> frameContextBuilder ) {
        super( block, null, accumulator, null, null );
        this.frame = frameContextBuilder;
    }


    private WinAggFrameResultContext getFrame() {
        return frame.apply( currentBlock() );
    }


    @Override
    public final List<Expression> arguments( Expression rowIndex ) {
        return rowTranslator( rowIndex ).translateList( rexArguments() );
    }


    @Override
    public Expression computeIndex( Expression offset, WinAggImplementor.SeekType seekType ) {
        return getFrame().computeIndex( offset, seekType );
    }


    @Override
    public Expression rowInFrame( Expression rowIndex ) {
        return getFrame().rowInFrame( rowIndex );
    }


    @Override
    public Expression rowInPartition( Expression rowIndex ) {
        return getFrame().rowInPartition( rowIndex );
    }


    @Override
    public RexToLixTranslator rowTranslator( Expression rowIndex ) {
        return getFrame().rowTranslator( rowIndex ).setNullable( currentNullables() );
    }


    @Override
    public Expression compareRows( Expression a, Expression b ) {
        return getFrame().compareRows( a, b );
    }


    @Override
    public Expression index() {
        return getFrame().index();
    }


    @Override
    public Expression startIndex() {
        return getFrame().startIndex();
    }


    @Override
    public Expression endIndex() {
        return getFrame().endIndex();
    }


    @Override
    public Expression hasRows() {
        return getFrame().hasRows();
    }


    @Override
    public Expression getFrameRowCount() {
        return getFrame().getFrameRowCount();
    }


    @Override
    public Expression getPartitionRowCount() {
        return getFrame().getPartitionRowCount();
    }
}

