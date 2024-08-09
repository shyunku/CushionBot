export class VirtualElement {
  /**
   *
   * @param {string} tag
   * @param {Object} props
   * @param {VirtualElement[]|string} children
   */
  constructor(tag, props, children) {
    this.tag = tag;
    this.props = props;
    this.children = children;
  }

  /**
   * @param {string} html
   * @returns {VirtualElement[] | string}
   */
}

export class HtmlParser {
  constructor(executor) {
    this.executor = executor;
  }

  parseHtml(html) {
    const children = this.parse(html);
    return new VirtualElement(null, {}, children);
  }

  /**
   * @param {string} html
   * @returns {VirtualElement[] | string}
   */
  parse(html) {
    html = html.trim();

    const subRootNodes = [];
    let subRootChildrenStartIndex = 0;
    let layer = 0;
    let subRootIndex = 0;

    let node = null;
    for (let i = 0; i < html.length; i++) {
      const char = html[i];
      if (char === "<") {
        const tagEndIndex = this.findCloseTagIndex(html, i);
        const enclosed = html.substring(i + 1, tagEndIndex);

        // console.log("layer", layer, "enclosed", enclosed);

        if (enclosed.endsWith("/")) {
          if (layer === 0) {
            // self closing tag
            const { tag, props } = this.parseProps(enclosed.slice(0, -1));
            subRootNodes.push(new VirtualElement(tag, props, []));
          }
        } else if (enclosed.startsWith("/")) {
          // closing tag
          layer--;

          if (layer < 0) {
            throw new Error(`Invalid closing tag at ${html.substring(i - 10, i + 10)}`);
          } else if (layer == 0) {
            const childHtml = html.substring(subRootChildrenStartIndex, i);
            const children = this.parse(childHtml);
            node.children = children;
            subRootNodes.push(node);
            node = null;
          } else {
            // pass
          }
          subRootIndex++;
        } else {
          // opening tag
          if (layer === 0) {
            let { tag, props } = this.parseProps(enclosed);
            node = new VirtualElement(tag, props, []);
            subRootChildrenStartIndex = tagEndIndex + 1;
          }
          layer++;
        }
      }
    }

    if (node != null)
      throw new Error(
        `Invalid closing tag at ...${html.substring(subRootChildrenStartIndex - 10, subRootChildrenStartIndex + 10)}...`
      );
    else if (layer > 0) {
      throw new Error(`Invalid closing tag (layer unmatched: ${layer})`);
    }

    if (subRootNodes.length === 0) {
      if (html.length === 0) return null;
      html = html.replace(/\{([^\{\}]+)\}/g, (match, p1) => this.executor(p1));
      if (html.includes("{") && html.includes("}")) {
        return this.executor(html);
      }
      return html;
    }
    return subRootNodes;
  }

  findCloseTagIndex(html, after) {
    let tagEndIndex = -1;
    let bracketLayer = 0;
    for (let j = after + 1; j < html.length; j++) {
      if (html[j] === "{") {
        bracketLayer++;
      } else if (html[j] === "}") {
        bracketLayer--;
      } else if (html[j] === ">" && bracketLayer === 0) {
        tagEndIndex = j;
        break;
      }
    }
    if (tagEndIndex === -1) {
      throw new Error(`Invalid syntax: can't find closing tag`);
    }
    return tagEndIndex;
  }

  parseProps(content) {
    content = content.trim();
    const tagRegex = /^(\w+)/;
    const regex = /(\w+)=\{([^}]+)\}|(\w+)=\"([^"]+)\"/g;
    const propMatches = content.match(regex);
    const props = {};
    const tag = content.match(tagRegex)?.[0];

    if (tag == null) {
      throw new Error(`Invalid tag: ${content}`);
    }

    if (propMatches != null) {
      for (let match of propMatches) {
        let [key, ...values] = match.split("=");
        let value = values.join("=");

        if (value.startsWith("{") && value.endsWith("}")) {
          value = value.slice(1, -1);
        } else {
          value = value.replace(/\{([^\{\}]+)\}/g, (match, p1) => this.executor(p1));
        }
        props[key] = this.executor(value);
        // console.log("key", key, "value", value, "calc", props[key]);
      }
    }
    return { tag, props };
  }
}
