import info.laht.threekt.math.Color
import info.laht.threekt.math.Vector3
import info.laht.threekt.textures.Texture

data class WaterParams(
  val waterNormals: Texture,
  val alpha: Double,
  val sunDirection: Vector3,
  val sunColor: Color,
  val waterColor: Color,
  val distortionScale: Double
)