import Home from "./components/Home.js";
import Sidebar from "./components/sidebar.js";
import UI from "./utils/ui.js";

UI.registerComponent("Home", Home);
UI.registerComponent("Sidebar", Sidebar);

document.addEventListener("DOMContentLoaded", () => {
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
