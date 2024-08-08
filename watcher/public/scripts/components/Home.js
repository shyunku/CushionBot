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
      },
    });
  }

  async afterMount() {
    await this.loadData();
    this.startSSE();
  }

  async loadData() {
    this.setState("data", async (prev) => {
      const data = { ...prev };
      const rawData = await Http.get("/data");
      if (
        this.states.selectedGuildId == null ||
        (this.states.selectedGuildId != null && rawData[this.states.selectedGuildId] == null)
      ) {
        this.setState("selectedGuildId", Object.keys(rawData)?.[0] ?? null);
        localStorage.setItem("selected_guild_id", selectedGuildId);
      }

      for (let guildId in rawData) {
        let guild = data[guildId];
        if (guild == null) {
          // get the data for the guild
          guild = await Guild.create(guildId);
          data[guildId] = guild;
        }

        await guild.updateSessions(rawData[guildId]);
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
      };
    } else {
      console.log("SSE not supported in this browser.");
    }
  }

  define() {
    return `
      <div id="home">
        <Sidebar data={this.states.data}/>
        <div id="main_content"></div>
      </div>
    `;
  }
}

export default Home;
