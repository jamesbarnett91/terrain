import info.laht.threekt.THREE
import info.laht.threekt.cameras.PerspectiveCamera
import info.laht.threekt.core.Geometry
import info.laht.threekt.external.controls.OrbitControls
import info.laht.threekt.external.libs.datgui.GUIParams
import info.laht.threekt.external.libs.datgui.NumberController
import info.laht.threekt.external.libs.datgui.dat
import info.laht.threekt.geometries.BoxGeometry
import info.laht.threekt.geometries.PlaneGeometry
import info.laht.threekt.lights.AmbientLight
import info.laht.threekt.lights.DirectionalLight
import info.laht.threekt.lights.Light
import info.laht.threekt.loaders.TextureLoader
import info.laht.threekt.materials.Material
import info.laht.threekt.materials.MeshPhongMaterial
import info.laht.threekt.materials.ShaderMaterial
import info.laht.threekt.math.Color
import info.laht.threekt.math.ColorConstants
import info.laht.threekt.objects.Mesh
import info.laht.threekt.renderers.WebGLRenderer
import info.laht.threekt.renderers.WebGLRendererParams
import info.laht.threekt.scenes.Scene
import info.laht.threekt.textures.CubeTexture
import info.laht.threekt.textures.Texture
import org.w3c.fetch.Request
import kotlin.browser.document
import kotlin.browser.window
import kotlin.dom.addClass
import kotlin.dom.removeClass
import kotlin.math.PI
import kotlin.math.pow

class Terrain {

  private val renderer: WebGLRenderer
  private val scene: Scene = Scene()
  private val camera: PerspectiveCamera
  private val controls: OrbitControls
  private lateinit var simplexNoise: SimplexNoise
  private lateinit var terrainMaterial: Material
  private lateinit var terrainGeometry: PlaneGeometry
  private lateinit var terrainMesh: Mesh
  private lateinit var waterGeometry: Geometry
  private lateinit var waterTexture: Texture
  private lateinit var waterMesh: Mesh
  private var light: Light

  private val size: Int = 256

  var options = GenerateOptions(
    80,
    2.9,
    4,
    16,
    false
  )

  init {

    initGui()

    scene.add(AmbientLight(0xeeeeee))

    light = DirectionalLight(0xffffff, 1)
      .apply {
        castShadow = true
        position.set(60, 170, -110)
      }
      .also(scene::add)

    camera = PerspectiveCamera(75, window.innerWidth.toDouble() / window.innerHeight, 0.1, 1000)
    camera.position.set(0, 49, 65)

    renderer = WebGLRenderer(WebGLRendererParams(antialias = true))
      .apply {
        setClearColor(ColorConstants.skyblue, 1)
        setSize(window.innerWidth, window.innerHeight)
      }

    controls = OrbitControls(camera, renderer.domElement)
    controls.autoRotate = true

    initSkybox()

    seedNoise()

    generateTerrain()

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
    regenerateTerrain()
  }

  private fun generateTerrain() {

    val genStart = window.performance.now()

    if (this::terrainMesh.isInitialized) {
      terrainMaterial.dispose()
      terrainGeometry.dispose()
      scene.remove(terrainMesh)
    }

    terrainGeometry = PlaneGeometry(100, 100, size - 1, size - 1)

    for (x in 0 until size) {
      for (y in 0 until size) {

        if (x == 0 || x == size - 1 || y == 0 || y == size - 1) {
          terrainGeometry.vertices[x + (y * size)].setZ(0)
        } else {

          var noise = simplexNoise.noise2D(x / options.zoomFactor.toDouble(), y / options.zoomFactor.toDouble())
          // add some fine noise
          noise += (0.03 * simplexNoise.noise2D(x / 15.toDouble(), y / 15.toDouble()))
          noise += (0.01 * simplexNoise.noise2D(x / 5.toDouble(), y / 5.toDouble()))
          noise += 2

          noise = noise.pow(options.scalingFactor)

          terrainGeometry.vertices[x + (y * size)].setZ(noise)
        }
      }
    }

    applyHeightMapColour(terrainGeometry)

    terrainGeometry.computeFaceNormals()
    terrainGeometry.computeVertexNormals()
    terrainGeometry.colorsNeedUpdate = true

    terrainMaterial = MeshPhongMaterial()
      .apply {
        if (options.showWireframe) {
          wireframe = true
          wireframeLinewidth = 1.0
        }
        vertexColors = THREE.FaceColors
      }

    terrainMesh = Mesh(terrainGeometry, terrainMaterial)
      .apply {
        receiveShadows = true
        rotation.x = -PI / 2
      }
      .also(scene::add)

    val genEnd = window.performance.now()
    println("Terrain generation took ${genEnd - genStart} ms")

    generateWater()
  }

  private fun regenerateTerrain() {
    document.getElementById("loading-overlay")?.removeClass("hidden")
    // without the setTimeout the loading overlay is never rendered
    window.setTimeout({
      generateTerrain()
      document.getElementById("loading-overlay")?.addClass("hidden")
    }, 10)
  }

  private fun generateWater() {

    if (this::waterMesh.isInitialized) {
      waterTexture.dispose()
      waterGeometry.dispose()
      scene.remove(waterMesh)
    }

    waterGeometry = BoxGeometry(99, 99, options.waterHeight)

    waterTexture = TextureLoader().load("waternormals.jpg", {
      it.wrapS = THREE.RepeatWrapping
      it.wrapT = THREE.RepeatWrapping
    })

    waterMesh = Water(
      waterGeometry,
      WaterParams(
        waterNormals = waterTexture,
        alpha = 1.0,
        sunDirection = light.position.clone().normalize(),
        sunColor = Color(0xffffff),
        waterColor = Color(0x001e0f),
        distortionScale = 5.0
      )
    )
      .apply {
        receiveShadows = true
        translateZ(options.waterHeight)
        rotation.x = -PI / 2
        position.set(0, options.waterHeight / 2, 0)
      }
      .also(scene::add)

    var waterMeshJsVar = waterMesh
    js("waterMeshJsVar.material.uniforms.size.value = 7")
  }

  private fun applyHeightMapColour(planeGeometry: PlaneGeometry) {
    planeGeometry.faces.forEach { face ->
      val vertexes = listOf(planeGeometry.vertices[face.a].z, planeGeometry.vertices[face.b].z, planeGeometry.vertices[face.c].z)
      val min = vertexes.min()!!

      if (min < options.snowHeightThreshold) {
        face.color?.set(ColorConstants.forestgreen)
      } else if (min >= options.snowHeightThreshold) {
        face.color?.set(ColorConstants.floralwhite)
      }
    }
  }

  // TODO add wrapper for CubeTextureLoader and ShaderLib
  private fun initSkybox() {
    val cubeTexture = js(
      """new THREE.CubeTextureLoader()
      .setPath('skybox/')
      .load([
        'px.jpg',
        'nx.jpg',
        'py.jpg',
        'ny.jpg',
        'pz.jpg',
        'nz.jpg'
      ]);"""
    ) as CubeTexture

    cubeTexture.format = THREE.RGBFormat

    val cubeShader = js("THREE.ShaderLib['cube'];")
    js("cubeShader.uniforms['tCube'].value = cubeTexture;")

    val skyboxMaterial = js(
      """new THREE.ShaderMaterial({
      fragmentShader: cubeShader.fragmentShader,
      vertexShader: cubeShader.vertexShader,
      uniforms: cubeShader.uniforms,
      depthWrite: false,
      side: THREE.BackSide
      });"""
    ) as ShaderMaterial

    Mesh(BoxGeometry(1000000, 1000000, 1000000), skyboxMaterial)
      .also(scene::add)
  }

  @Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")
  fun initGui() {

    window.fetch(Request("presets.json")).then { r ->
      r.text().then { presets ->

        dat.GUI(
          GUIParams(
            closed = false,
            width = 250,
            load = JSON.parse(presets)

          )
        ).also {
          it.remember(this.options)

          (it.add(this.options, "zoomFactor") as NumberController).apply {
            min(10)
              .max(200)
              .step(5)
              .onFinishChange { regenerateTerrain() }
          }

          (it.add(this.options, "scalingFactor") as NumberController).apply {
            min(0)
              .max(4)
              .step(0.05)
              .onFinishChange { regenerateTerrain() }
          }

          (it.add(this.options, "waterHeight") as NumberController).apply {
            min(0)
              .max(30)
              .step(1)
              .onChange { generateWater() }
          }

          (it.add(this.options, "snowHeightThreshold") as NumberController).apply {
            min(0)
              .max(30)
              .step(1)
              .onFinishChange { regenerateTerrain() }
          }

          it.add(this, "reseedNoise")
          it.add(this.options, "showWireframe").onChange { regenerateTerrain() }
          it.add(controls, "autoRotate")
        }
      }
    }
  }

  fun animate() {

    window.setTimeout({

      window.requestAnimationFrame {
        if (controls.autoRotate) {
          controls.update()
//        println("x:${camera.position.x} y: ${camera.position.y} z:${camera.position.z}")
        }
        var waterMeshJsVar = waterMesh
        js("waterMeshJsVar.material.uniforms[ 'time' ].value += 1.0 / 60.0;")
        animate()
      }

    }, 1000 / 30)

    renderer.render(scene, camera)
  }
}