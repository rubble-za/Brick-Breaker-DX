package com.brickbreaker.engine

/**
 * A level is a grid of characters: '.' empty, '1'..'3' bricks with that many hits,
 * '#' an indestructible brick. All rows must be [Levels.COLS] wide.
 */
class Level(val rows: List<String>) {
    init {
        require(rows.all { it.length == Levels.COLS }) { "every row must be ${Levels.COLS} columns" }
    }
}

object Levels {
    const val COLS = 9

    val all: List<Level> = listOf(
        Level(
            listOf(
                "111111111",
                "111111111",
                "111111111",
                "111111111",
            )
        ),
        Level(
            listOf(
                "2.2.2.2.2",
                ".1.1.1.1.",
                "2.2.2.2.2",
                ".1.1.1.1.",
                "2.2.2.2.2",
            )
        ),
        Level(
            listOf(
                "333333333",
                "2#2#2#2#2",
                "111111111",
                "1#1#1#1#1",
                "111111111",
            )
        ),
        Level(
            listOf(
                "....3....",
                "...222...",
                "..11111..",
                ".2222222.",
                "..11111..",
                "...222...",
                "....3....",
            )
        ),
        Level(
            listOf(
                "3.3.3.3.3",
                "#.#.#.#.#",
                "2.2.2.2.2",
                "#.#.#.#.#",
                "1.1.1.1.1",
            )
        ),
        Level(
            listOf(
                "333333333",
                "3.......3",
                "3.22222.3",
                "3.2###2.3",
                "3.22222.3",
                "3.......3",
                "333333333",
            )
        ),
        Level(
            listOf(
                "1111#1111",
                "2222#2222",
                "3333.3333",
                "2222#2222",
                "1111#1111",
            )
        ),
        Level(
            listOf(
                "333333333",
                "322222223",
                "321111123",
                "322222223",
                "333333333",
            )
        ),
    )
}
