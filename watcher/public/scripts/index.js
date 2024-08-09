import Home from "./components/home.js";
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

// $(document).ready(async () => {
//   data = {};
//   guilds = {};
//   users = {};

//   selectedGuildId = localStorage.getItem("selected_guild_id") ?? Object.keys(data)?.[0] ?? null;
//   localStorage.setItem("selected_guild_id", selectedGuildId);

//   // resize detect
//   window.addEventListener("resize", () => {
//     if (selectedGuildId != null) {
//       displayMainContent();
//     }
//   });

//   if (channelId != null) {
//     const sidebarElem = $(".sidebar")[0];
//     sidebarElem.style.display = "none";
//     selectedGuildId = channelId;
//   }

//   setTimeout(async () => {
//     await loadData();
//     startSSE();

//     console.log("data", data);
//     console.log("guilds", guilds);
//     console.log("users", users);
//   }, 500);
// });
