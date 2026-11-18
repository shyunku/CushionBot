import { fastInterval } from "../utils/common.js";
import UI from "../utils/ui.js";

class Sidebar extends UI {
  constructor(props) {
    super(props);
  }

  define() {
    return `
      <div id="sidebar">
        <div class="title">서버 연결 현황</div>
        <div id="today">
          ${UI.$$(Object.keys(this.states.data ?? {}), (guildId) => {
            return `
              <TodayItem $guild={$data?.["${guildId}"]} 
                         $selected={$selectedGuildId == "${guildId}"}
                         $onGuildIdSelect={$onGuildIdSelect}/>
              `;
          })}
        </div>
      </div>
    `;
  }
}

export default Sidebar;
