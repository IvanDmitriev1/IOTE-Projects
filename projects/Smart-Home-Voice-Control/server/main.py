from collections.abc import Callable
from contextlib import AbstractContextManager, asynccontextmanager
from threading import Lock

from fastapi import FastAPI, Request
from fastapi.responses import PlainTextResponse

from hardware import Hardware, open_hardware
from operations import forecast, light_color, pressure_mmhg


def create_app(
    hardware_factory: Callable[[], AbstractContextManager[Hardware]] = open_hardware,
) -> FastAPI:
    io_lock = Lock()

    @asynccontextmanager
    async def lifespan(app: FastAPI):
        with hardware_factory() as hardware:
            app.state.hardware = hardware
            yield

    app = FastAPI(
        lifespan=lifespan, redirect_slashes=False,
        openapi_url=None, docs_url=None, redoc_url=None,
    )

    @app.get("/{command}", response_class=PlainTextResponse)
    def handle_command(request: Request, command: str) -> PlainTextResponse:
        hardware: Hardware = request.app.state.hardware
        path = f"/{command}"
        color = light_color(path)

        try:
            with io_lock:
                if path == "/temperature":
                    return PlainTextResponse(str(round(hardware.temperature())))
                if path == "/pressure":
                    return PlainTextResponse(str(pressure_mmhg(hardware.pressure())))
                if path == "/humidity":
                    if hardware.humidity is None:
                        return PlainTextResponse("Humidity unavailable on BMP280", status_code=501)
                    return PlainTextResponse(str(round(hardware.humidity())))
                if path == "/weather":
                    return PlainTextResponse(forecast(hardware.pressure()))
                if color is not None:
                    hardware.set_color(color)
                    return PlainTextResponse(path, headers={"Cache-Control": "no-store"})
        except (OSError, RuntimeError):
            headers = {"Cache-Control": "no-store"} if color is not None else None
            return PlainTextResponse("Hardware unavailable", status_code=503, headers=headers)

        return PlainTextResponse("Not Found", status_code=404)

    return app


app = create_app()
