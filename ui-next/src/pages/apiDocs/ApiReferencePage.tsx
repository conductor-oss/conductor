import { useEffect } from "react";
import { useNavigate } from "react-router-dom";

const getSwaggerUrl = () =>
  `//${window.location.host}/swagger-ui/index.html?configUrl=/api-docs/swagger-config#/`;

export default function ApiReferencePage() {
  const navigate = useNavigate();

  useEffect(() => {
    window.open(getSwaggerUrl(), "_blank", "noopener,noreferrer");
    navigate(-1);
  }, [navigate]);

  return null;
}
