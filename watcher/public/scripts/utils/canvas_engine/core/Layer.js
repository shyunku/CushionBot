import Callable from "./Callable.js";
import CanvasEngine from "./CanvasEngine.js";

class Layer extends Callable {
  constructor(contextName = "") {
    super();

    this.ref = null;
    this.drawableLayers = [];

    this.contextName = contextName;

    this.enableHoverDisplay = false;
    this.enableSelectionDisplay = false;
    this.enableForceHoverDisplay = false;

    this.shouldRerender = true;
    this.alwaysRerender = true;
  }

  /**
   *
   * @param {any} c
   * @param {CanvasEngine} engine
   */
  draw(c, engine) {
    console.log("Layer Draw Method not overrided!");
  }

  optimizeRender() {
    this.alwaysRerender = false;
  }

  renderCompleted() {
    if (this.alwaysRerender) return;
    this.shouldRerender = false;
  }

  shouldRerender() {
    this.shouldRerender = true;
  }

  drawLayers(c, ref, tool, w, h) {
    for (let layer of this.drawableLayers) {
      layer.draw(this.context, this, this.tool, this.width, this.height);
      ref.initOptions();
    }
  }

  getCSSprop() {
    if (this.enableSelectionDisplay) return "selected";
    if (this.enableForceHoverDisplay || this.enableHoverDisplay) return "hovered";
    return "";
  }

  registerLayer(layer) {
    this.drawableLayers.push(layer);
  }
}

export default Layer;
