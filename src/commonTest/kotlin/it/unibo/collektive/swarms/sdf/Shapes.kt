package it.unibo.collektive.swarms.sdf

import it.unibo.collektive.swarms.geometry.Position
import it.unibo.collektive.swarms.sdf.primitive.Circle
import it.unibo.collektive.swarms.sdf.shape.CircularSector
import it.unibo.collektive.swarms.sdf.shape.Crescent
import it.unibo.collektive.swarms.sdf.shape.CutDisk
import it.unibo.collektive.swarms.sdf.shape.FibonacciSpiral
import it.unibo.collektive.swarms.sdf.shape.Hexagon
import it.unibo.collektive.swarms.sdf.shape.Horseshoe
import it.unibo.collektive.swarms.sdf.shape.QuestionMark
import it.unibo.collektive.swarms.sdf.shape.RegularPolygon
import it.unibo.collektive.swarms.sdf.shape.Ring
import it.unibo.collektive.swarms.sdf.shape.RoundedX
import it.unibo.collektive.swarms.sdf.shape.Spiral
import it.unibo.collektive.swarms.sdf.shape.Square
import it.unibo.collektive.swarms.sdf.shape.Stairs
import it.unibo.collektive.swarms.sdf.shape.Star
import it.unibo.collektive.swarms.sdf.shape.Triangle
import it.unibo.collektive.swarms.sdf.shape.Vesica
import it.unibo.collektive.swarms.sdf.text.TextBlock

/** The shapes of the experiments in the paper (`shapes.yml`), all centered around (50, 50), by name. */
internal val paperShapes: Map<String, SDF> = mapOf(
    "circle" to Circle(Position(50.0, 50.0), 30.0),
    "star" to Star(Position(50.0, 50.0), 50.0, 5),
    "star8" to Star(Position(50.0, 50.0), 30.0, 8, spikiness = 4.0),
    "hexagon" to Hexagon(Position(50.0, 50.0), 30.0),
    "pentagon" to RegularPolygon(Position(50.0, 50.0), 30.0, 5, rotation = 1.5707963),
    "square" to Square(Position(50.0, 50.0), 50.0),
    "triangle" to Triangle(Position(20.0, 32.667), Position(80.0, 32.667), Position(50.0, 84.667)),
    "ring" to Ring(Position(50.0, 50.0), 25.0, 6.0),
    "crescent" to Crescent(Position(50.0, 50.0), 30.0, 20.0),
    "horseshoe" to Horseshoe(Position(50.0, 50.0), 20.0, 0.7853982, 15.0, 6.0),
    "circular_sector" to CircularSector(Position(50.0, 32.5), 35.0, 1.0471976),
    "cut_disk" to CutDisk(Position(50.0, 50.0), 30.0, -10.0),
    "vesica" to Vesica(Position(50.0, 50.0), 35.0, 20.0),
    "rounded_x" to RoundedX(Position(50.0, 50.0), 45.0, 6.0),
    "stairs" to Stairs(Position(20.0, 26.0), 15.0, 12.0, 4),
    "question_mark" to QuestionMark(Position(50.0, 70.0), 20.0, 4.0),
    "spiral" to Spiral(Position(50.0, 50.0), 12.0, 3, thickness = 4.0),
    "fibonacci_spiral" to FibonacciSpiral(Position(50.0, 50.0), 5.5, 7, thickness = 4.0),
    "letters_seams" to TextBlock("SEAMS", Position(41.0, 32.0), 36.0, 4.0),
)
