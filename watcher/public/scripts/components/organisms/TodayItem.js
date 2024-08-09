import UI from "../../utils/ui.js";

class TodayItem extends UI {
  constructor(props) {
    super(props);
  }

  afterMount() {
    // console.log(this);
  }

  onClick(e) {
    this.states.onGuildIdSelect?.(this.states.guild?.id);
  }

  define() {
    return `
      <div class="today-item server-{$guild?.id} {$selected ? 'selected' : ''}" onclick={this.onClick}>
        <div class="header">
          <img src={$guild?.iconUrl} alt="Server Icon" class="icon" />
          <div class="name">{$guild?.name}</div>
        </div>
        <div class="content">
          <canvas id="today_chart_{$guild?.id}" class="today-chart"></canvas>
        </div>
      </div>
    `;
  }
}

export default TodayItem;
