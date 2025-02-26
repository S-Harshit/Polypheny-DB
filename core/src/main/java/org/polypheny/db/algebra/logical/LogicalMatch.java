/*
 * Copyright 2019-2021 The Polypheny Project
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

package org.polypheny.db.algebra.logical;


import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import org.polypheny.db.algebra.AlgCollation;
import org.polypheny.db.algebra.AlgNode;
import org.polypheny.db.algebra.AlgShuttle;
import org.polypheny.db.algebra.core.Match;
import org.polypheny.db.algebra.type.AlgDataType;
import org.polypheny.db.plan.AlgOptCluster;
import org.polypheny.db.plan.AlgTraitSet;
import org.polypheny.db.plan.Convention;
import org.polypheny.db.rex.RexNode;


/**
 * Sub-class of {@link Match} not targeted at any particular engine or calling convention.
 */
public class LogicalMatch extends Match {

    /**
     * Creates a LogicalMatch.
     *
     * @param cluster cluster
     * @param traitSet Trait set
     * @param input Input relational expression
     * @param rowType Row type
     * @param pattern Regular Expression defining pattern variables
     * @param strictStart Whether it is a strict start pattern
     * @param strictEnd Whether it is a strict end pattern
     * @param patternDefinitions Pattern definitions
     * @param measures Measure definitions
     * @param after After match definitions
     * @param subsets Subset definitions
     * @param allRows Whether all rows per match (false means one row per match)
     * @param partitionKeys Partition by columns
     * @param orderKeys Order by columns
     * @param interval Interval definition, null if WITHIN clause is not defined
     */
    private LogicalMatch(
            AlgOptCluster cluster, AlgTraitSet traitSet, AlgNode input, AlgDataType rowType, RexNode pattern, boolean strictStart, boolean strictEnd, Map<String, RexNode> patternDefinitions,
            Map<String, RexNode> measures, RexNode after, Map<String, ? extends SortedSet<String>> subsets, boolean allRows, List<RexNode> partitionKeys, AlgCollation orderKeys, RexNode interval ) {
        super( cluster, traitSet, input, rowType, pattern, strictStart, strictEnd, patternDefinitions, measures, after, subsets, allRows, partitionKeys, orderKeys, interval );
    }


    /**
     * Creates a LogicalMatch.
     */
    public static LogicalMatch create(
            AlgNode input, AlgDataType rowType, RexNode pattern, boolean strictStart, boolean strictEnd, Map<String, RexNode> patternDefinitions, Map<String, RexNode> measures,
            RexNode after, Map<String, ? extends SortedSet<String>> subsets, boolean allRows, List<RexNode> partitionKeys, AlgCollation orderKeys, RexNode interval ) {
        final AlgOptCluster cluster = input.getCluster();
        final AlgTraitSet traitSet = cluster.traitSetOf( Convention.NONE );
        return new LogicalMatch( cluster, traitSet, input, rowType, pattern, strictStart, strictEnd, patternDefinitions, measures, after, subsets, allRows, partitionKeys, orderKeys, interval );
    }


    @Override
    public Match copy(
            AlgNode input, AlgDataType rowType, RexNode pattern, boolean strictStart, boolean strictEnd, Map<String, RexNode> patternDefinitions, Map<String, RexNode> measures, RexNode after,
            Map<String, ? extends SortedSet<String>> subsets, boolean allRows, List<RexNode> partitionKeys, AlgCollation orderKeys, RexNode interval ) {
        final AlgTraitSet traitSet = getCluster().traitSetOf( Convention.NONE );
        return new LogicalMatch( getCluster(), traitSet, input, rowType, pattern, strictStart, strictEnd, patternDefinitions, measures, after, subsets, allRows, partitionKeys, orderKeys, interval );
    }


    @Override
    public AlgNode accept( AlgShuttle shuttle ) {
        return shuttle.visit( this );
    }

}

