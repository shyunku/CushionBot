class Callable {
  constructor() {
    this.listeners = [];
  }

  registerListener = (eventName, func) => {
    this.listeners[eventName] = func;
  };

  call = (eventName, ...arg) => {
    if (this.listeners.hasOwnProperty(eventName) === false) {
      console.error(`EventListener named "${eventName}" is not registered!`);
      return;
    }

    let caller = this.listeners[eventName];
    caller(...arg);
  };
}

export default Callable;
