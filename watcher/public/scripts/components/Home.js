import Guild from "../objects/Guild.js";
import Http from "../utils/http.js";
import UI from "../utils/ui.js";

class Home extends UI {
  constructor(props) {
    super({
      ...props,
      initialState: {
        data: {},
        selectedGuildId: localStorage.getItem("selected_guild_id") ?? null,
        loadingTotal: 1,
        loadingCurrent: 0,
      },
    });
  }

  async afterMount() {
    await this.loadData();
    this.startSSE();
  }

  onGuildIdSelect = (guildId) => {
    localStorage.setItem("selected_guild_id", guildId);
    this.setState("selectedGuildId", guildId);
  };

  define() {
    return `
      <div id="home">
        <div id="loader">
          <div class="filler {$loadingCurrent == $loadingTotal ? 'filled' : ''}" style="width: {$loadingCurrent*100/$loadingTotal}%;"></div>
        </div>
        <Sidebar $data={$data} 
                 $selectedGuildId={$selectedGuildId}
                 $onGuildIdSelect={this.onGuildIdSelect}/>
        <MainContent $guild={$data?.[$selectedGuildId]}/>
      </div>
    `;
  }

  async loadData() {
    console.log("update data");
    let currentAcc = 0;
    let total = 0;
    this.setState("data", async (prev) => {
      const data = { ...prev };
      const rawData = await Http.get("/data");
      if (
        this.states.selectedGuildId == null ||
        (this.states.selectedGuildId != null && rawData[this.states.selectedGuildId] == null)
      ) {
        this.setState("selectedGuildId", Object.keys(rawData)?.[0] ?? null);
        localStorage.setItem("selected_guild_id", this.states.selectedGuildId);
      }

      total = Object.values(rawData).reduce((acc, guild) => acc + Object.keys(guild).length, 0);
      this.setState("loadingTotal", total);

      for (let guildId in rawData) {
        let guild = data[guildId];
        if (guild == null) {
          // get the data for the guild
          guild = await Guild.create(guildId);
          data[guildId] = guild;
        }

        await guild.updateSessions(rawData[guildId], (curr, total) => {
          this.setState("loadingCurrent", currentAcc + curr);
        });

        currentAcc += Object.keys(rawData[guildId]).length;
      }

      return data;
    });
  }

  startSSE() {
    if (!!window.EventSource) {
      const source = new EventSource(`http://${botHost}:${botPort}/sse`);

      // disconnect when the page is closed
      $(window).bind("beforeunload", (e) => {
        if (source) {
          console.log("sse closed");
          source.close();
        }
      });

      source.onopen = (event) => {
        console.log("SSE opened: ", event);
      };

      source.onmessage = (event) => {
        try {
          const message = JSON.parse(event.data);
          const { type, data } = message;
          this.loadData();
        } catch (err) {
          console.error("Error parsing message: ", event.data);
        }
      };

      source.onerror = (event) => {
        console.error("EventSource failed: ", event);
        // reconnect
        source.close();
        setTimeout(() => {
          this.startSSE();
        }, 1000);
      };
    } else {
      console.log("SSE not supported in this browser.");
    }
  }
}

export default Home;
