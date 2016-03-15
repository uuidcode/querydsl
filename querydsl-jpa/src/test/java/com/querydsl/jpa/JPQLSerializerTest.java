/*
 * Copyright 2015, The Querydsl Team (http://www.querydsl.com/team)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.querydsl.jpa;

import static com.querydsl.jpa.JPAExpressions.selectOne;
import static org.junit.Assert.assertEquals;

import java.util.Arrays;

import org.junit.Test;

import com.querydsl.core.DefaultQueryMetadata;
import com.querydsl.core.JoinType;
import com.querydsl.core.QueryMetadata;
import com.querydsl.core.domain.QCat;
import com.querydsl.core.types.EntityPath;
import com.querydsl.core.types.Expression;
import com.querydsl.core.types.Path;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.dsl.EntityPathBase;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberPath;
import com.querydsl.jpa.domain.JobFunction;
import com.querydsl.jpa.domain.Location;
import com.querydsl.jpa.domain.QDomesticCat;
import com.querydsl.jpa.domain.QEmployee;

public class JPQLSerializerTest {

    @Test
    public void and_or() {
        //A.a.id.eq(theId).and(B.b.on.eq(false).or(B.b.id.eq(otherId)));
        QCat cat = QCat.cat;
        Predicate pred = cat.id.eq(1).and(cat.name.eq("Kitty").or(cat.name.eq("Boris")));
        JPQLSerializer serializer = new JPQLSerializer(HQLTemplates.DEFAULT);
        serializer.handle(pred);
        assertEquals("cat.id = ?1 and (cat.name = ?2 or cat.name = ?3)", serializer.toString());
        assertEquals("cat.id = 1 && (cat.name = Kitty || cat.name = Boris)", pred.toString());
    }

    @Test
    public void case1() {
        QCat cat = QCat.cat;
        JPQLSerializer serializer = new JPQLSerializer(JPQLTemplates.DEFAULT);
        Expression<?> expr = Expressions.cases().when(cat.toes.eq(2)).then(2)
                .when(cat.toes.eq(3)).then(3)
                .otherwise(4);
        serializer.handle(expr);
        assertEquals("case when (cat.toes = ?1) then ?1 when (cat.toes = ?2) then ?2 else ?3 end", serializer.toString());
    }

    @Test
    public void case1_hibernate() {
        QCat cat = QCat.cat;
        JPQLSerializer serializer = new JPQLSerializer(HQLTemplates.DEFAULT);
        Expression<?> expr = Expressions.cases().when(cat.toes.eq(2)).then(2)
                .when(cat.toes.eq(3)).then(3)
                .otherwise(4);
        serializer.handle(expr);
        assertEquals("case when (cat.toes = ?1) then ?1 when (cat.toes = ?2) then ?2 else 4 end", serializer.toString());
    }

    @Test
    public void case2() {
        QCat cat = QCat.cat;
        JPQLSerializer serializer = new JPQLSerializer(JPQLTemplates.DEFAULT);
        Expression<?> expr = Expressions.cases().when(cat.toes.eq(2)).then(cat.id.multiply(2))
                .when(cat.toes.eq(3)).then(cat.id.multiply(3))
                .otherwise(4);
        serializer.handle(expr);
        assertEquals("case when (cat.toes = ?1) then (cat.id * ?1) when (cat.toes = ?2) then (cat.id * ?2) else ?3 end", serializer.toString());
    }

    @Test
    public void case2_hibernate() {
        QCat cat = QCat.cat;
        JPQLSerializer serializer = new JPQLSerializer(HQLTemplates.DEFAULT);
        Expression<?> expr = Expressions.cases().when(cat.toes.eq(2)).then(cat.id.multiply(2))
                .when(cat.toes.eq(3)).then(cat.id.multiply(3))
                .otherwise(4);
        serializer.handle(expr);
        assertEquals("case when (cat.toes = ?1) then (cat.id * ?1) when (cat.toes = ?2) then (cat.id * ?2) else 4 end", serializer.toString());
    }

    @Test
    public void fromWithCustomEntityName() {
        JPQLSerializer serializer = new JPQLSerializer(HQLTemplates.DEFAULT);
        EntityPath<Location> entityPath = new EntityPathBase<Location>(Location.class, "entity");
        QueryMetadata md = new DefaultQueryMetadata();
        md.addJoin(JoinType.DEFAULT, entityPath);
        serializer.serialize(md, false, null);
        assertEquals("select entity\nfrom Location2 entity", serializer.toString());
    }

    @Test
    public void join_with() {
        QCat cat = QCat.cat;
        JPQLSerializer serializer = new JPQLSerializer(HQLTemplates.DEFAULT);
        QueryMetadata md = new DefaultQueryMetadata();
        md.addJoin(JoinType.DEFAULT, cat);
        md.addJoin(JoinType.INNERJOIN, cat.mate);
        md.addJoinCondition(cat.mate.alive);
        serializer.serialize(md, false, null);
        assertEquals("select cat\nfrom Cat cat\n  inner join cat.mate with cat.mate.alive", serializer.toString());
    }

    @Test
    public void normalizeNumericArgs() {
        JPQLSerializer serializer = new JPQLSerializer(HQLTemplates.DEFAULT);
        NumberPath<Double> doublePath = Expressions.numberPath(Double.class, "doublePath");
        serializer.handle(doublePath.add(1));
        serializer.handle(doublePath.between((float) 1.0, 1L));
        serializer.handle(doublePath.lt((byte) 1));
        for (Object constant : serializer.getConstantToLabel().keySet()) {
            assertEquals(Double.class, constant.getClass());
        }
    }

    @Test
    public void delete_clause_uses_dELETE_fROM() {
        QEmployee employee = QEmployee.employee;
        JPQLSerializer serializer = new JPQLSerializer(HQLTemplates.DEFAULT);
        QueryMetadata md = new DefaultQueryMetadata();
        md.addJoin(JoinType.DEFAULT, employee);
        md.addWhere(employee.lastName.isNull());
        serializer.serializeForDelete(md);
        assertEquals("delete from Employee employee\nwhere employee.lastName is null", serializer.toString());
    }

    @Test
    public void delete_with_subQuery() {
        QCat parent = QCat.cat;
        QCat child = new QCat("kitten");

        JPQLSerializer serializer = new JPQLSerializer(HQLTemplates.DEFAULT);
        QueryMetadata md = new DefaultQueryMetadata();
        md.addJoin(JoinType.DEFAULT, child);
        md.addWhere(
            child.id.eq(1)
            .and(selectOne()
                .from(parent)
                .where(parent.id.eq(2), child.in(parent.kittens)).exists()));
        serializer.serializeForDelete(md);
        assertEquals("delete from Cat kitten\n" +
                "where kitten.id = ?1 and exists (select 1\n" +
            "from Cat cat\nwhere cat.id = ?2 and kitten member of cat.kittens)", serializer.toString());
    }

    @Test
    public void in() {
        JPQLSerializer serializer = new JPQLSerializer(HQLTemplates.DEFAULT);
        serializer.handle(Expressions.numberPath(Integer.class, "id").in(Arrays.asList(1, 2)));
        assertEquals("id in (?1)", serializer.toString());
    }

    @Test
    public void not_in() {
        JPQLSerializer serializer = new JPQLSerializer(HQLTemplates.DEFAULT);
        serializer.handle(Expressions.numberPath(Integer.class, "id").notIn(Arrays.asList(1, 2)));
        assertEquals("id not in (?1)", serializer.toString());
    }

    @Test
    public void like() {
        JPQLSerializer serializer = new JPQLSerializer(HQLTemplates.DEFAULT);
        serializer.handle(Expressions.stringPath("str").contains("abc!"));
        assertEquals("str like ?1 escape '!'", serializer.toString());
        assertEquals("%abc!!%", serializer.getConstantToLabel().keySet().iterator().next().toString());
    }

    @Test
    public void stringContainsIc() {
        JPQLSerializer serializer = new JPQLSerializer(HQLTemplates.DEFAULT);
        serializer.handle(Expressions.stringPath("str").containsIgnoreCase("ABc!"));
        assertEquals("lower(str) like ?1 escape '!'", serializer.toString());
        assertEquals("%abc!!%", serializer.getConstantToLabel().keySet().iterator().next().toString());
    }

    @Test
    public void substring() {
        JPQLSerializer serializer = new JPQLSerializer(HQLTemplates.DEFAULT);
        QCat cat = QCat.cat;
        serializer.handle(cat.name.substring(cat.name.length().subtract(1), 1));
        assertEquals("substring(cat.name,length(cat.name) + ?1,?2 - (length(cat.name) - ?2))", serializer.toString());
    }

    @Test
    public void nullsFirst() {
        QCat cat = QCat.cat;
        JPQLSerializer serializer = new JPQLSerializer(HQLTemplates.DEFAULT);
        QueryMetadata md = new DefaultQueryMetadata();
        md.addJoin(JoinType.DEFAULT, cat);
        md.addOrderBy(cat.name.asc().nullsFirst());
        serializer.serialize(md, false, null);
        assertEquals("select cat\n" +
                 "from Cat cat\n" +
                 "order by cat.name asc nulls first", serializer.toString());
    }

    @Test
    public void nullsLast() {
        QCat cat = QCat.cat;
        JPQLSerializer serializer = new JPQLSerializer(HQLTemplates.DEFAULT);
        QueryMetadata md = new DefaultQueryMetadata();
        md.addJoin(JoinType.DEFAULT, cat);
        md.addOrderBy(cat.name.asc().nullsLast());
        serializer.serialize(md, false, null);
        assertEquals("select cat\n" +
                     "from Cat cat\n" +
                     "order by cat.name asc nulls last", serializer.toString());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void treat() {
        QCat cat = QCat.cat;
        JPQLSerializer serializer = new JPQLSerializer(HQLTemplates.DEFAULT);
        QueryMetadata md = new DefaultQueryMetadata();
        md.addJoin(JoinType.DEFAULT, cat);
        md.addJoin(JoinType.JOIN, cat.mate.as((Path) QDomesticCat.domesticCat));
        md.setProjection(QDomesticCat.domesticCat);
        serializer.serialize(md, false, null);
        assertEquals("select domesticCat\n" +
                "from Cat cat\n" +
                "  inner join treat(cat.mate as DomesticCat) as domesticCat", serializer.toString());
    }

    @Test
    public void openJPA_variables() {
        QCat cat = QCat.cat;
        JPQLSerializer serializer = new JPQLSerializer(OpenJPATemplates.DEFAULT);
        QueryMetadata md = new DefaultQueryMetadata();
        md.addJoin(JoinType.DEFAULT, cat);
        md.addJoin(JoinType.INNERJOIN, cat.mate);
        md.addJoinCondition(cat.mate.alive);
        serializer.serialize(md, false, null);
        assertEquals("select cat_\nfrom Cat cat_\n  inner join cat_.mate on cat_.mate.alive",
                serializer.toString());
    }

    @Test
    public void visitLiteral_boolean() {
        JPQLSerializer serializer = new JPQLSerializer(HQLTemplates.DEFAULT);
        serializer.visitLiteral(Boolean.TRUE);
        assertEquals("true", serializer.toString());
    }

    @Test
    public void visitLiteral_number() {
        JPQLSerializer serializer = new JPQLSerializer(HQLTemplates.DEFAULT);
        serializer.visitLiteral(1.543);
        assertEquals("1.543", serializer.toString());
    }

    @Test
    public void visitLiteral_string() {
        JPQLSerializer serializer = new JPQLSerializer(HQLTemplates.DEFAULT);
        serializer.visitLiteral("abc''def");
        assertEquals("'abc''''def'", serializer.toString());
    }

    @Test
    public void visitLiteral_enum() {
        JPQLSerializer serializer = new JPQLSerializer(HQLTemplates.DEFAULT);
        serializer.visitLiteral(JobFunction.MANAGER);
        assertEquals("com.querydsl.jpa.domain.JobFunction.MANAGER", serializer.toString());
    }

    @Test
    public void substring_indexOf() {
        QCat cat = QCat.cat;
        JPQLSerializer serializer = new JPQLSerializer(HQLTemplates.DEFAULT);
        cat.name.substring(cat.name.indexOf("")).accept(serializer, null);
        assertEquals("substring(cat.name,locate(?1,cat.name)-1 + ?2)", serializer.toString());
    }
}
