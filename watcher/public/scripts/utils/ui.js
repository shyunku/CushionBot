import { deepCompare, isClass, trimAndClearString } from "./common.js";
import Crypto from "./crypto.js";
import OrderedHashMap from "./data_structures/OrderedHashMap.js";
import { HtmlParser, VirtualElement } from "./html.js";

const DebugMode = false;

const NativeHTMLTags = [
  "html",
  "head",
  "title",
  "base",
  "link",
  "meta",
  "style",
  "script",
  "noscript",
  "template",
  "body",
  "section",
  "nav",
  "article",
  "aside",
  "h1",
  "h2",
  "h3",
  "h4",
  "h5",
  "h6",
  "header",
  "footer",
  "address",
  "main",
  "p",
  "hr",
  "pre",
  "blockquote",
  "ol",
  "ul",
  "li",
  "dl",
  "dt",
  "dd",
  "figure",
  "figcaption",
  "div",
  "a",
  "em",
  "strong",
  "small",
  "s",
  "cite",
  "q",
  "dfn",
  "abbr",
  "data",
  "time",
  "code",
  "var",
  "samp",
  "kbd",
  "sub",
  "sup",
  "i",
  "b",
  "u",
  "mark",
  "ruby",
  "rt",
  "rp",
  "bdi",
  "bdo",
  "span",
  "br",
  "wbr",
  "ins",
  "del",
  "img",
  "iframe",
  "embed",
  "object",
  "param",
  "video",
  "audio",
  "source",
  "track",
  "canvas",
  "map",
  "area",
  "svg",
  "math",
  "table",
  "caption",
  "colgroup",
  "col",
  "tbody",
  "thead",
  "tfoot",
  "tr",
  "td",
  "th",
  "form",
  "fieldset",
  "legend",
  "label",
  "input",
  "button",
  "select",
  "datalist",
  "optgroup",
  "option",
  "textarea",
  "keygen",
  "output",
  "progress",
  "meter",
  "details",
  "summary",
  "menuitem",
  "menu",
];

export default class UI {
  static root = null;
  static components = {};

  constructor(
    props = {
      tag: null,
      key: null,
      initialState: {},
      parent: null,
      parentNode: null,
      template: null,
      native: false,
      layer: 0,
    }
  ) {
    this.tag = props?.tag ?? null;
    this.key = props?.key ?? null;

    this.uuid = Crypto.uuid();
    this.layer = props?.layer ?? 0;

    this.native = props?.native ?? false;
    this.mounted = false;
    this.active = true;
    this.template = props?.template ?? null;
    this.lastParsedTree = null;
    this.parser = new HtmlParser((value) => {
      try {
        value = value.replace(/\$/g, "this.states.");
        // console.log("eval", value, "->", eval(value), this.states);
        return eval(value);
      } catch (e) {
        if (this.native) {
          return this.parent.parser.executor(value);
        } else {
          console.error(`Error parsing value: ${value}`);
          throw e;
        }
      }
    });

    this.node = null;
    this.parent = props?.parent ?? null; // indicates the parent component (not the parent node)
    this.parentNode = props?.parentNode ?? document.body;
    this.children = new OrderedHashMap();

    this.eventListeners = {};

    this.parentStates = this.native ? props.parent.states : null;
    this.states = new Proxy(props?.initialState ?? {}, {
      set: (target, key, value) => {
        const prevState = { ...target };
        let newState = null;
        if (key === "replace") newState = { ...value };
        else newState = { ...target, [key]: value };

        if (key === "replace") {
          for (let key in value) {
            target[key] = value[key];
          }
        } else {
          target[key] = value;
        }

        if (!this.stateLock) {
          this.rerender(prevState, newState);
        }

        return true;
      },
      get: (target, key, receiver) => {
        if (this.native) {
          return this.parent.states[key];
        } else {
          const value = target[key];
          if (value instanceof Function) {
            return function (...args) {
              return value.apply(this === receiver ? target : this, args);
            };
          }
          return value;
        }
      },
    });
    this.stateLock = false;

    if (window.components == null) {
      window.components = {};
    }
    window.components[this.uuid] = this;
  }

  shouldRerender(prevState, newState) {
    return true;
  }
  afterMount() {}
  afterUpdate() {}
  beforeRender() {}
  afterRender() {}
  beforeUnmount() {}
  setStates(newState) {
    if (this.native) {
      this.parent.setStates(newState);
    } else {
      this.states.replace = { ...newState };
    }
  }
  async setState(key, newValue) {
    if (this.native) {
      this.parent.setState(key, newValue);
    } else {
      if (typeof newValue === "function") {
        this.states[key] = await newValue(this.states[key]);
      } else {
        this.states[key] = newValue;
      }
    }
  }
  setSilentStates(newState) {
    // console.log(this.uuid, "silent set", newState);
    this.stateLock = true;
    this.states.replace = { ...newState };
    this.stateLock = false;
  }

  rerender(prevState, newState) {
    this.log(`>> [${this.uuid}] rerender requested for replace value:`, prevState, "->", newState);

    if (!this.shouldRerender(prevState, newState)) return;
    this.render();
  }

  render(tree = this.parseTemplate()) {
    this.stateLock = true;
    this.beforeRender();
    this.stateLock = false;

    this.lastParsedTree = tree;
    this.log(`======================= Render ${this.uuid} =======================`, tree, this);
    this.log("> Parsed Tree", tree, this.define());

    // mount if not mounted
    let newlyMounted = false;
    if (!this.mounted) {
      if (UI.isComponent(tree.tag)) throw new Error("Component root must be a native HTML tag");
      this.stateLock = true;
      this.mount(tree.tag, tree.props);
      this.stateLock = false;
      newlyMounted = true;
    } else {
      this.applyProps(tree.props);
    }

    // inactive all children
    this.children.foreach((child, key) => {
      child.active = false;
    });

    // mount children if not mounted
    const children = tree.children;
    if (Array.isArray(children)) {
      // this.log("children", children);

      for (let childIndex = 0; childIndex < children.length; childIndex++) {
        const child = children[childIndex];
        this.log("Render child", child);
        const key = child.props?.key ?? Crypto.hash(`${this.uuid}-${childIndex}`);
        let childComponent = this.children.get(key);
        if (childComponent == null) {
          // create new component
          const newComponent = this.create(child.tag, child.props);
          newComponent.key = key;

          this.children.add(key, newComponent);
          childComponent = newComponent;
        }

        childComponent.setSilentStates(child.props);

        if (childComponent.native) {
          childComponent.render(child);
        } else {
          // childComponent.template = child.children;
          childComponent.render();
        }

        childComponent.active = true;
      }
    } else {
      if (this.node.innerHTML !== children) this.node.innerHTML = children;
    }

    // unmount inactive children
    this.children.foreach((child, key) => {
      if (!child.active) {
        child.unmount();
        this.children.remove(key);
      }
    });

    if (newlyMounted) {
      this.afterMount();
    } else {
      this.afterUpdate();
    }
    this.afterRender();
    this.log(`======================= Render ${this.uuid} Done =======================`, tree);
  }

  /**
   * @returns {VirtualElement}
   */
  parseTemplate() {
    const template = this.define();
    if (template == null) {
      return null;
    }

    const parseTree = this.parser.parseHtml(template);
    if (Array.isArray(parseTree.children)) {
      const size = parseTree.children.length;
      if (size > 1) throw new Error("Component element must be a single element");
    } else if (typeof parseTree.children === "string") {
      throw new Error("Component element must not be string");
    } else {
      throw new Error("Invalid tree structure");
    }
    return parseTree.children[0];
  }

  isTreeUpdated() {
    const lastTree = { ...this.lastParsedTree };
    const currentTree = this.parseTemplate();
    return !deepCompare(lastTree, currentTree);
  }

  forceUpdate() {
    this.render();
  }

  create(className, props) {
    if (!UI.isComponent(className) && !UI.isNativeHTMLTag(className)) {
      throw new Error(`Component ${className} is not registered`);
    }

    const native = UI.isNativeHTMLTag(className);
    const Component = UI.components[className] ?? NativeHTMLComponent;
    const parent = this.native ? this.parent : this;

    const args = {
      key: props?.key ?? null,
      tag: native ? className : null,
      initialState: props,
      parentNode: this.node,
      parent,
      native,
      layer: this.layer + 1,
    };

    this.log(`> Create Component`, className, props, args, "in", this.node);

    return new Component(args);
  }

  unmount() {
    this.log(`> unmount`, this);
    this.beforeUnmount();
    for (let child of this.children) {
      child.unmount();
      this.children.remove(child.key);
    }
    this.node.remove();
    this.mounted = false;
  }

  mount(tag, props) {
    this.log(`> mount ${tag} with props`, props, this);
    const node = document.createElement(tag);
    this.node = node;

    if (props?.id != null) node.id = props?.id;
    this.applyProps(props);
    node.setAttribute("data-uuid", this.uuid);
    this.parentNode.appendChild(node);
    this.mounted = true;
    return node;
  }

  applyProps(props) {
    // remove all listeners
    for (let topic in this.eventListeners) {
      const listeners = this.eventListeners[topic];
      for (let listener of listeners) {
        this.node.removeEventListener(topic, listener);
      }
    }

    for (let key in props) {
      if (key.startsWith("$")) continue;
      if (key.startsWith("on")) {
        const topic = key.slice(2).toLowerCase();
        const listener = (...args) => {
          const func = props[key] ?? null;
          func.call(this, ...args);
        };

        if (!this.eventListeners[topic]) this.eventListeners[topic] = [];
        this.node.addEventListener(topic, listener);
        this.eventListeners[topic].push(listener);
        continue;
      }
      this.node.setAttribute(key, props[key]);
    }
  }

  define() {
    const attributes = Object.keys(this.states)
      .filter((key) => !key.startsWith("$"))
      .map((key) => ` ${key}={${JSON.stringify(this.states[key])}}`)
      .join("");
    return `<${this.tag} ${attributes}>${this.template ?? ""}</${this.tag}>`;
  }

  log(...args) {
    if (!DebugMode) return;
    console.debug("\t".repeat(this.layer), ...args);
  }

  /* ------------------------------------ static functions ------------------------------------ */
  static isComponent(className) {
    return UI.components[className] != null;
  }

  static isNativeHTMLTag(tag) {
    return NativeHTMLTags.includes(tag);
  }

  static $createRoot(template) {
    const root = new UI({ tag: "div", id: "root" });
    root.define = () => `<div id="root">${template}</div>`;
    root.isRoot = true;
    return root;
  }

  static registerComponent(component) {
    if (!isClass(component)) throw new Error(`Given component is not valid component.`);
    if (!(component.prototype instanceof UI)) throw new Error(`Given component is not UI Component.`);

    const className = component.name;
    if (className == null) {
      console.error(component);
      throw new Error(`Can't find constructor name`);
    }
    UI.components[className] = component;
  }

  // static $(selector) {
  //   return document.querySelector(selector);
  // }

  // static $$(selector) {
  //   return document.querySelectorAll(selector);
  // }

  static $$(target, mapper) {
    return target.map(mapper).join("");
  }
}

class NativeHTMLComponent extends UI {
  constructor(props) {
    super(props);
  }

  shouldRerender(prevState, newState) {
    console.log("should rerender", prevState, newState, !deepCompare(prevState, newState));
    return !deepCompare(prevState, newState);
  }
}
