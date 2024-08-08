import { deepCompare, trimAndClearString } from "./common.js";
import Crypto from "./crypto.js";
import OrderedHashMap from "./data_structures/OrderedHashMap.js";

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

const componentRootRegex = /^<(\w+)(\s+[^>]+)?>([\s\S]*?)<\/\1>$|^<(\w+)(\s+[^>]+)?\/>$/g;
const componentRootDetailRegex = /^<(\w+)(\s+[^>]+)?>([\s\S]*?)<\/\1>$|^<(\w+)(\s+[^>]+)?\/>$/i;
const componentRegex = /<(\w+)(\s+[^>]+)?>([\s\S]*?)<\/\1>|<(\w+)(\s+[^>]+)?\/>/g;
const componentDetailRegex = /<(\w+)(\s+[^>]+)?>([\s\S]*?)<\/\1><?|<(\w+)(\s+[^>]+)?\/>/i;
const attrRegex = /(\w+)=\{([^}]+)\}|(\w+)=\"([^"]+)\"/g;
const attrDetailRegex = /(\w+)=\{([^}]+)\}|(\w+)=\"([^"]+)\"/i;

export default class UI {
  static root = null;
  static components = {};

  constructor(
    props = {
      id: null,
      tag: null,
      key: null,
      initialState: {},
      definition: null,
      native: false,
      virtual: false,
      parentNode: null,
    }
  ) {
    this.tag = props?.tag ?? null;
    this.id = props?.id ?? null;
    this.key = props?.key ?? null;
    this.states = new Proxy(props?.initialState ?? {}, {
      set: (target, key, value) => {
        this.rerender(target, key, value);
        return true;
      },
    });

    this.uuid = Crypto.uuid();

    this.native = props?.native ?? false;
    this.virtual = props?.virtual ?? false;
    this.mounted = false;

    this.node = null;
    this.parentNode = props?.parentNode ?? document.body;
    this.children = new OrderedHashMap();
    this.inheritedDefinition = props?.definition ?? null;
    this.definition = trimAndClearString(this.define() ?? this.inheritedDefinition);

    // console.debug(`created ${this.id}`, this);
  }

  shouldRerender(prevState, newState) {
    return true;
  }
  afterMount() {}
  afterUpdate() {}
  beforeUnmount() {}

  setStates(newState) {
    this.states.replace = { ...newState };
  }

  async setState(key, newValue) {
    if (typeof newValue === "function") {
      this.states[key] = await newValue(this.states[key]);
    } else {
      this.states[key] = newValue;
    }
  }

  rerender(target, key, value) {
    const prevState = { ...target };
    let newState = null;
    if (key === "replace") {
      console.debug(`[${this.uuid}] rerender requested for replace value:`, value);
      newState = { ...value };
      for (let key in value) {
        target[key] = value[key];
      }
    } else {
      console.debug(`[${this.uuid}] rerender requested for ${key} with value:`, value);
      newState = { ...target, [key]: value };
      target[key] = value;
    }

    if (!this.shouldRerender(prevState, newState)) return;
    this.render();
  }

  render() {
    // console.log(this.define(), this.inheritedDefinition);
    this.definition = trimAndClearString(this.define() ?? this.inheritedDefinition);
    console.debug("=== Rendering", this.id, this.definition, this.states);

    // parse definition
    const parsed = this.parseDefinition();
    if (parsed == null) return;

    const { className, props, children } = parsed;
    console.debug("== Parsed", parsed);

    // check if it's created
    if (!this.mounted) {
      if (UI.isComponent(className)) {
        // wrapper?
        throw new Error("Component root must be a native HTML tag");
      }
      this.node = this.mount(className, this.native ? this.states : props);
    }

    // render children
    for (let childIndex = 0; childIndex < children.length; childIndex++) {
      const child = children[childIndex];
      // console.debug("rendering child", child);
      if (typeof child === "string") {
        this.node.innerHTML = child;
        continue;
      }

      const { className: childClassName, props: childProps, rawChildren: childRawChildren } = child;
      // key 추론
      const key =
        childProps?.key ?? childProps?.id != null
          ? Crypto.hash(childProps.id)
          : Crypto.hash(`${this.uuid}-${childIndex}`);

      let component = this.children.get(key);
      if (component == null) {
        component = this.create(childClassName, childProps, childRawChildren, childIndex);
        this.children.add(component.key, component);
      } else {
        console.debug(`Component ${component.id} already exists`);
      }

      // console.log(component);

      if (!component.mounted || component.shouldRerender()) {
        component.inheritedDefinition = childRawChildren;
        component.setStates({ ...component.states, ...childProps });
        // component.render(this.node, this);
      } else {
        console.log(`Skipping rendering ${component.id}`);
      }
    }

    this.afterUpdate();
  }

  parseDefinition() {
    if (this.definition == null) return null;
    const componentMatches = this.definition.match(componentRootRegex);
    if (componentMatches != null) {
      return this.parseComponent(true, this.definition);
    } else if (this.native) {
      return { className: this.tag, props: {}, children: [this.definition] };
    } else {
      console.error("Invalid definition", this.definition);
    }
    return null;
  }

  parseComponent(isWrapper = false, raw) {
    const regex = isWrapper ? componentRootRegex : componentRegex;
    const detailRegex = isWrapper ? componentRootDetailRegex : componentDetailRegex;

    const componentMatches = raw.match(regex);
    if (componentMatches != null) {
      const componentInnerMatches = raw.match(detailRegex);
      let className = componentInnerMatches[1] ?? componentInnerMatches[4];
      let rawProps = componentInnerMatches[2] ?? componentInnerMatches[5] ?? null;
      let rawChildren = componentInnerMatches[3]?.trim() ?? null;

      let props = {};
      const attrMatches = rawProps?.match(attrRegex) ?? [];
      for (let attrMatch of attrMatches) {
        const attrInnerMatches = attrMatch.match(attrDetailRegex);
        const key = attrInnerMatches[1] ?? attrInnerMatches[3];
        const value = attrInnerMatches[2] != null ? eval(`${attrInnerMatches[2]}`) : attrInnerMatches[4];
        props[key] = value;
      }

      const children = [];
      if (rawChildren != null && !this.virtual) {
        const childrenMatches = rawChildren.match(componentRegex);
        if (childrenMatches != null) {
          let index = 0;
          for (let match of childrenMatches) {
            const detailChildMatches = match.match(componentDetailRegex);
            const childClassName = detailChildMatches[1] ?? detailChildMatches[4];
            const childRawProps = detailChildMatches[2] ?? detailChildMatches[5] ?? null;

            let childProps = {};
            const childAttrMatches = childRawProps?.match(attrRegex) ?? [];
            for (let attrMatch of childAttrMatches) {
              const attrInnerMatches = attrMatch.match(attrDetailRegex);
              const key = attrInnerMatches[1] ?? attrInnerMatches[3];
              const value = attrInnerMatches[2] != null ? eval(`${attrInnerMatches[2]}`) : attrInnerMatches[4];
              childProps[key] = value;
            }

            let child = null;
            if (UI.isComponent(childClassName)) {
              const virtualComponent = this.create(childClassName, {}, null, index, true);
              child = virtualComponent.parseDefinition();
              child.className = childClassName;
            } else {
              child = this.parseComponent(true, match);
            }

            if (child != null) {
              child.props = { ...child.props, ...childProps };
              children.push(child);
            }

            index++;
          }
        }
      }

      return { className, props, children: children, rawChildren: rawChildren };
    } else {
      return null;
    }
  }

  create(className, props, definition = null, index = 0, virtual = false) {
    if (!UI.isComponent(className) && !UI.isNativeHTMLTag(className)) {
      throw new Error(`Component ${className} is not registered`);
    }

    // console.log("create", className, props);

    const native = UI.isNativeHTMLTag(className);
    const Component = UI.components[className] ?? NativeHTMLComponent;
    const id = props?.id ?? null;
    delete props.id;

    return new Component({
      ...props,
      id,
      key: id != null ? Crypto.hash(id) : Crypto.hash(`${this.uuid}-${index}`),
      virtual,
      definition,
      initialState: props,
      tag: native ? className : null,
      parentNode: this.node,
      native,
    });
  }

  unmount() {
    this.beforeUnmount();
    for (let child of this.children) {
      child.unmount();
      this.children.remove(child.key);
    }
    this.node.remove();
    this.mounted = false;
  }

  mount(tag, props) {
    console.debug(`mounting ${tag} with props`, props, this);
    const node = document.createElement(tag);

    if (this.id != null) node.id = this.id;
    for (let key in props) {
      if (key === "key" || key === "id") continue;
      node.setAttribute(key, props[key]);
    }
    this.parentNode.appendChild(node);
    this.mounted = true;
    this.afterMount();
    return node;
  }

  define() {
    return null;
  }

  /* ------------------------------------ static functions ------------------------------------ */
  static isComponent(className) {
    return UI.components[className] != null;
  }

  static isNativeHTMLTag(tag) {
    return NativeHTMLTags.includes(tag);
  }

  static $createRoot(definition) {
    const root = new UI({ id: "root", definition: `<div id="root">${definition}</div>` });
    root.isRoot = true;
    return root;
  }

  static registerComponent(className, component) {
    UI.components[className] = component;
  }

  static $(selector) {
    return document.querySelector(selector);
  }

  static $$(selector) {
    return document.querySelectorAll(selector);
  }
}

class NativeHTMLComponent extends UI {
  constructor(props) {
    super(props);
  }

  shouldRerender(prevState, newState) {
    return true || !deepCompare(prevState, newState);
  }

  define() {
    return null;
  }
}
