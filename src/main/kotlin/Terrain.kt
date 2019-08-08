import info.laht.threekt.THREE
import info.laht.threekt.cameras.PerspectiveCamera
import info.laht.threekt.external.controls.OrbitControls
import info.laht.threekt.external.libs.datgui.GUIParams
import info.laht.threekt.external.libs.datgui.NumberController
import info.laht.threekt.external.libs.datgui.dat
import info.laht.threekt.geometries.BoxGeometry
import info.laht.threekt.geometries.PlaneGeometry
import info.laht.threekt.lights.AmbientLight
import info.laht.threekt.lights.PointLight
import info.laht.threekt.materials.MeshBasicMaterial
import info.laht.threekt.materials.MeshPhongMaterial
import info.laht.threekt.math.ColorConstants
import info.laht.threekt.objects.Mesh
import info.laht.threekt.renderers.WebGLRenderer
import info.laht.threekt.renderers.WebGLRendererParams
import info.laht.threekt.scenes.Scene
import kotlin.browser.document
import kotlin.browser.window
import kotlin.math.pow

class Terrain {

  private val renderer: WebGLRenderer
  private val scene: Scene = Scene()
  private val camera: PerspectiveCamera
  private val controls: OrbitControls
  private lateinit var simplexNoise: SimplexNoise
  private lateinit var terrainMesh: Mesh
  private lateinit var waterMesh: Mesh

  private val size: Int = 256

  enum class ScalingType {
    LINEAR, EXPONENTIAL
  }

  var zoomFactor = 80
  var scalingFactor = 11
  var scalingType = ScalingType.LINEAR
  var autoRotate = false

  var waterHeight = 5
  var snowHeightThreshold = 15

  var showWireframe = false

  init {

    scene.add(AmbientLight(0xeeeeee))

    PointLight(0xffffff)
      .apply {
        castShadow = true
        position.set(0, 90, 200)
      }.also(scene::add)

    camera = PerspectiveCamera(75, window.innerWidth.toDouble() / window.innerHeight, 0.1, 1000)
    camera.position.setZ(45)
    camera.position.setY(-73)

    renderer = WebGLRenderer(WebGLRendererParams(antialias = true))
      .apply {
        setClearColor(ColorConstants.black, 1)
        setSize(window.innerWidth, window.innerHeight)
      }

    controls = OrbitControls(camera, renderer.domElement)

    initGui()

    seedNoise()

    generateTerrain()

    generateWater()

    window.addEventListener("resize", {
      camera.aspect = window.innerWidth.toDouble() / window.innerHeight
      camera.updateProjectionMatrix()

      renderer.setSize(window.innerWidth, window.innerHeight)
    }, false)

    document.getElementById("container")
      ?.apply {
        appendChild(renderer.domElement)
      }

  }

  private fun seedNoise() {
    simplexNoise = js("new SimplexNoise()") as SimplexNoise
  }

  fun reseedNoise() {
    seedNoise()
    generateTerrain()
  }

  private fun generateTerrain() {

    if (this::terrainMesh.isInitialized) {
      scene.remove(terrainMesh)
    }

    val terrainGeom = PlaneGeometry(100,100, size-1, size-1)

    for (x in 0 until size) {
      for (y in 0 until size) {

        if (x == 0 || x == size-1 || y == 0 || y == size-1) {
          terrainGeom.vertices[x + (y*size)].setZ(0)
        } else {

          var noise = simplexNoise.noise2D(x / zoomFactor.toDouble(), y / zoomFactor.toDouble())
          noise += (0.01 * simplexNoise.noise2D(x.toDouble(), y.toDouble()))
          noise += 1

          // have to toString here for JS interop
          if (scalingType.toString() == ScalingType.LINEAR.toString()) {
            noise *= scalingFactor
          } else {
            noise = noise.pow(scalingFactor)
          }

          terrainGeom.vertices[x + (y * size)].setZ(noise)
        }
      }
    }

    applyHeightMapColour(terrainGeom)

    terrainGeom.computeFaceNormals()
    terrainGeom.computeVertexNormals()
    terrainGeom.colorsNeedUpdate = true

    val terrainMaterial = MeshPhongMaterial()
      .apply {
        if (showWireframe) {
          wireframe = true
          wireframeLinewidth = 1.0
        }
        vertexColors = THREE.FaceColors
      }

    terrainMesh = Mesh(terrainGeom, terrainMaterial)
      .apply { receiveShadows = true }
      .also(scene::add)
  }

  private fun applyHeightMapColour(planeGeometry: PlaneGeometry) {
    planeGeometry.faces.forEach { face ->
      val vertexes = listOf(planeGeometry.vertices[face.a].z, planeGeometry.vertices[face.b].z, planeGeometry.vertices[face.c].z)
      val min = vertexes.min()!!

      if (min < snowHeightThreshold) {
        face.color?.set(ColorConstants.forestgreen)
      } else if (min >= snowHeightThreshold) {
        face.color?.set(ColorConstants.floralwhite)
      }
    }
  }

  private fun generateWater() {

    if (this::waterMesh.isInitialized) {
      scene.remove(waterMesh)
    }

    val waterGeom = BoxGeometry(99, 99, waterHeight)
    val waterMaterial = MeshBasicMaterial()
      .apply {
        color.set(ColorConstants.aqua)
        transparent = true
        opacity = 0.7
      }

    waterMesh = Mesh(waterGeom, waterMaterial)
      .also {
        scene.add(it)
        it.translateZ(waterHeight/2)
      }
  }

  @Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")
  fun initGui() {
    dat.GUI(
      GUIParams(
        closed = false
      )
    ).also {

      (it.add(this, "zoomFactor") as NumberController).apply {
        min(10)
        .max(200)
        .step(5)
        .onChange { generateTerrain() }
      }

      (it.add(this, "scalingFactor") as NumberController).apply {
        min(0)
        .max(50)
        .step(1)
        .onChange { generateTerrain() }
      }

      (it.add(this, "waterHeight") as NumberController).apply {
        min(0)
        .max(30)
        .step(1)
        .onChange { generateWater() }
      }

      (it.add(this, "snowHeightThreshold") as NumberController).apply {
        min(0)
        .max(30)
        .step(1)
        .onChange { generateTerrain() }
      }
      it.add(this, "scalingType", ScalingType.values()).onChange {
        generateTerrain()
      }
      it.add(this, "reseedNoise")
      it.add(this, "showWireframe").onChange { generateTerrain() }
      it.add(this, "autoRotate")
    }
  }

  fun animate() {

    window.requestAnimationFrame {
      if (autoRotate) {
        terrainMesh.rotation.z += 0.005
        waterMesh.rotation.z += 0.005
//        println("x:${camera.position.x} y: ${camera.position.y} z:${camera.position.z}")
      }
      animate()
    }

    renderer.render(scene, camera)
  }

}
