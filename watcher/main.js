import express from "express";
import bodyParser from "body-parser";
import cors from "cors";
import dotenv from "dotenv";
import { publicIpv4 } from "public-ip";

dotenv.config();

const app = express();
app.set("view engine", "ejs");
app.use(bodyParser.json());
app.use(cors());
app.use(express.static("public"));

app.get("/", async (req, res) => {
  res.render("index", {
    botHost: process.env.BOT_SERVER_HOST ?? (await publicIpv4()) ?? "localhost",
    botPort: process.env.BOT_SERVER_PORT ?? "7918",
    channelId: null,
  });
});

app.get("/channel/:id", async (req, res) => {
  res.render("index", {
    botHost: process.env.BOT_SERVER_HOST ?? (await publicIpv4()) ?? "localhost",
    botPort: process.env.BOT_SERVER_PORT ?? "7918",
    channelId: req.params.id,
  });
});

app.listen(7919, async () => {
  console.log(`Server is running on http://${await publicIpv4()}:7919`);
});
