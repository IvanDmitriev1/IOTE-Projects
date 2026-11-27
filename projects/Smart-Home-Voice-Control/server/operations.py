from random import randrange

RGB = tuple[int, int, int]

COLORS: dict[str, RGB] = {
    "/FFFFFF": (255, 255, 255),
    "/FF0000": (255, 0, 0),
    "/00FF00": (0, 255, 0),
    "/0000FF": (0, 0, 255),
    "/FFFF00": (255, 255, 0),
    "/FF00FF": (255, 0, 255),
    "/000000": (0, 0, 0),
}


def pressure_mmhg(pressure_hpa: float) -> int:
    return round(pressure_hpa / 1.33322387415)


def forecast(pressure_hpa: float) -> str:
    if pressure_hpa >= 1022.69:
        return "the weather will be stable"
    if pressure_hpa >= 1009.14:
        return "the weather will be cloudy"
    return "the weather will be rainy"


def light_color(path: str) -> RGB | None:
    if path == "/random":
        return randrange(256), randrange(256), randrange(256)
    color = COLORS.get(path)
    if color is not None:
        return color
    if len(path) == 7 and path[0] == "/":
        hex_digits = path[1:]
        if all(digit in "0123456789abcdefABCDEF" for digit in hex_digits):
            return tuple(int(hex_digits[index:index + 2], 16) for index in (0, 2, 4))
    return None
