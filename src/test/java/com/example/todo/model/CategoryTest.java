package com.example.todo.model;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Category エンティティの単純なgetter/setter・コンストラクタの挙動を検証するテストクラス。
 * </p>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CategoryTest {

    @Test
    @Order(1)
    void name_colorを受け取るコンストラクタで生成すると各値が設定される() {
        // （Category(String name, String color) コンストラクタが未網羅だったため）

        //------------実行--------------------------

        Category category = new Category("仕事", "#FF0000");

        //------------比較--------------------------

        assertThat(category.getName()).isEqualTo("仕事");
        assertThat(category.getColor()).isEqualTo("#FF0000");
        assertThat(category.getId()).isNull();
    }

    @Test
    @Order(2)
    void 各getter_setterに設定した値がそのまま取得できる() {
        // （setId等、アプリ内から未使用のgetter/setterを網羅する目的）

        //------------準備--------------------------

        Category category = new Category();

        //------------実行--------------------------

        category.setId(10L);
        category.setName("プライベート");
        category.setColor("#00FF00");

        //------------比較--------------------------

        assertThat(category.getId()).isEqualTo(10L);
        assertThat(category.getName()).isEqualTo("プライベート");
        assertThat(category.getColor()).isEqualTo("#00FF00");
    }
}
