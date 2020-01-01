# terrain

Procedural terrain generation in Kotlin and WebGL.

Demo [here](https://jamesbarnett.io/terrain)

![](https://jamesbarnett.io/files/terrain/terrain2.png)

The terrain height is determined using a [simplex noise](https://en.wikipedia.org/wiki/Simplex_noise) algorithm in multiple passes.

I created this project to play around with out Kotlin's [JavaScript transpilation](https://kotlinlang.org/docs/reference/js-overview.html) feature which allows Kotlin projects to run in the browser.

It makes use of Lars Ivar Hatledal's [kotlin wrapper](https://github.com/markaren/three-kt-wrapper) for Three.js, as well as my own very simple wrappers for [simplex-noise.js](https://github.com/jwagner/simplex-noise.js) and Jérémy Bouny's [water shader](https://github.com/jbouny/ocean).

There are currently some definite performance and resource deallocation issues which I will probably never get round to fixing. Repeated regeneration of the terrain will consume large amounts of memory.
