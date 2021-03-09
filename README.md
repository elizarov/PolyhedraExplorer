# Polyhedra Explorer

**🚧 Work in process.**
                                              
## Building & running

```shell
gradlew jsBrowserDevelopmentRun 
```

## Roadmap / TODO
                       
* Animations
  * [x] Animate all param changes
  * [x] Animate polyhedra transformations
  * [ ] Animate topology-preserving transitions (e.g. canonicalization)
  * [ ] Animate multi-step transformations
* Asynchrony 
  * [x] Move canonicalization algo to a WebWorker
  * [ ] Better progress bar display
* Export 
  * [ ] Solid to STL
  * [x] Geometry to OpenSCAD  
* Polyhedra
  * [ ] Bigger library of seeds
      * [x] Platonic solids
      * [x] Arhimedean solids
      * [ ] Catalan solids
      * [ ] Johnson solids
  * [ ] Identify names of well-know polyhedra
* Rendering
  * [ ] Render nicer edges and vertices
  * [ ] Render better-looking (physical) materials 
  * [ ] Color faces by geometry
  * [ ] Hollow models with select faces
* Interaction
  * [ ] Two-finger Z-rotate and zoom gestures
  * [ ] Select/show/hide faces by kind
* Polyhedron info
  * [ ] Show face and vertex geometry
  * [ ] Show edge dihedral angles
* Transformations
  * [ ] Merge face kind when possible
  * [ ] Redesign truncation algorithm so that it always works
  * [ ] Rectification solution for non-regular polyhedra  
  * [ ] Stellation
  * [x] Chamfering
* Custom transformations
  * [ ] Truncate specific vertices
  * [ ] Cantellate specific edges
  * [ ] Augment specific faces 
* Infrastructure    
  * [ ] Embed CSS into WebPack
  * [ ] `jsBrowserProductionRun` shall work
* Overall style / layout
  * [ ] Move inessential params to popup
  * [ ] Flip left-right through seeds and transforms
  * [ ] Design and implement a full-screen modern style
* Backend
  * [ ] Render polyhedra picture by params

