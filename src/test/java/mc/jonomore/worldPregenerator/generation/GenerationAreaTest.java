package mc.jonomore.worldPregenerator.generation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GenerationAreaTest {

  @Test
  void worldSpawnIsASquare() {
    assertEquals(new GenerationArea("square", 10.5, -3.5, 1200, 1200), GenerationArea.aroundWorldSpawn(10.5, -3.5, 1200));
  }

  @Test
  void spawnPointsAreEncasedWithMargin() {
    GenerationArea area = GenerationArea.aroundSpawnPoints(List.of(
        new SpawnPoint(-400, 70, 150),
        new SpawnPoint(300, 70, -200),
        new SpawnPoint(0, 70, 0)
    ), 128);

    assertEquals(new GenerationArea("rectangle", -50, -25, 350 + 128, 175 + 128), area);
  }

  @Test
  void singlePointIsASquareOfTheMargin() {
    assertEquals(new GenerationArea("rectangle", 5, 7, 64, 64),
        GenerationArea.aroundSpawnPoints(List.of(new SpawnPoint(5, 60, 7)), 64));
  }

  @Test
  void rejectsNoPoints() {
    assertThrows(IllegalArgumentException.class, () -> GenerationArea.aroundSpawnPoints(List.of(), 64));
  }
}
