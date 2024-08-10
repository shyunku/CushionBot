import Home from "./components/Home.js";
import MainContent from "./components/molecules/MainContent.js";
import TodayItem from "./components/molecules/TodayItem.js";
import Sidebar from "./components/sidebar.js";
import UI from "./utils/ui.js";

UI.registerComponent(Home);
UI.registerComponent(Sidebar);
UI.registerComponent(TodayItem);
UI.registerComponent(MainContent);

document.addEventListener("DOMContentLoaded", () => {
  // console.log(HtmlParser.parseHtml(sample));
  const root = UI.$createRoot(`
    <Home/>
  `);
  root.render();
  console.log(root.children.toArray());
});
