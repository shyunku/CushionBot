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

app.get("/", (req, res) => {
  res.render("index", {
    botHost: process.env.BOT_SERVER_HOST ?? "localhost",
    botPort: process.env.BOT_SERVER_PORT ?? "7918",
  });
});

app.listen(7919, async () => {
  console.log(`Server is running on http://${await publicIpv4()}:7919`);
});
