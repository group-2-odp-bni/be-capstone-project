#!/bin/bash
# Manage deployed services
# View status, restart, scale individual services

set -e

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

NAMESPACE="${NAMESPACE:-orange-wallet}"

# Function to show service status
show_status() {
    echo -e "${GREEN}============================================${NC}"
    echo -e "${GREEN}Orange Wallet Services Status${NC}"
    echo -e "${GREEN}============================================${NC}"
    echo ""

    SERVICES=("authentication-service" "user-service" "wallet-service" "transaction-service" "notification-worker")

    printf "%-25s %-10s %-15s %-10s %-20s\n" "SERVICE" "STATUS" "READY" "RESTARTS" "IMAGE"
    echo "────────────────────────────────────────────────────────────────────────────────────"

    for service in "${SERVICES[@]}"; do
        POD=$(kubectl get pods -n $NAMESPACE -l app=$service -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || echo "")

        if [ -z "$POD" ]; then
            printf "%-25s %-10s %-15s %-10s %-20s\n" "$service" "NOT DEPLOYED" "-" "-" "-"
        else
            STATUS=$(kubectl get pod $POD -n $NAMESPACE -o jsonpath='{.status.phase}')
            READY=$(kubectl get pod $POD -n $NAMESPACE -o jsonpath='{.status.containerStatuses[0].ready}')
            RESTARTS=$(kubectl get pod $POD -n $NAMESPACE -o jsonpath='{.status.containerStatuses[0].restartCount}')
            IMAGE=$(kubectl get pod $POD -n $NAMESPACE -o jsonpath='{.spec.containers[0].image}' | cut -d':' -f2)

            if [ "$READY" = "true" ]; then
                READY_COLOR=$GREEN
            else
                READY_COLOR=$RED
            fi

            printf "%-25s %-10s ${READY_COLOR}%-15s${NC} %-10s %-20s\n" "$service" "$STATUS" "$READY" "$RESTARTS" "$IMAGE"
        fi
    done

    echo ""
}

# Function to restart a service
restart_service() {
    SERVICE=$1

    if [ -z "$SERVICE" ]; then
        echo -e "${RED}Error: Service name required${NC}"
        return 1
    fi

    echo -e "${YELLOW}Restarting $SERVICE...${NC}"
    kubectl rollout restart deployment/$SERVICE -n $NAMESPACE
    kubectl rollout status deployment/$SERVICE -n $NAMESPACE --timeout=300s
    echo -e "${GREEN}✓ $SERVICE restarted${NC}"
}

# Function to scale a service
scale_service() {
    SERVICE=$1
    REPLICAS=$2

    if [ -z "$SERVICE" ] || [ -z "$REPLICAS" ]; then
        echo -e "${RED}Error: Service name and replica count required${NC}"
        return 1
    fi

    echo -e "${YELLOW}Scaling $SERVICE to $REPLICAS replicas...${NC}"
    kubectl scale deployment/$SERVICE --replicas=$REPLICAS -n $NAMESPACE
    kubectl rollout status deployment/$SERVICE -n $NAMESPACE --timeout=300s
    echo -e "${GREEN}✓ $SERVICE scaled to $REPLICAS${NC}"
}

# Function to view logs
view_logs() {
    SERVICE=$1
    LINES=${2:-50}

    if [ -z "$SERVICE" ]; then
        echo -e "${RED}Error: Service name required${NC}"
        return 1
    fi

    POD=$(kubectl get pods -n $NAMESPACE -l app=$SERVICE -o jsonpath='{.items[0].metadata.name}' 2>/dev/null)

    if [ -z "$POD" ]; then
        echo -e "${RED}Error: No pod found for $SERVICE${NC}"
        return 1
    fi

    echo -e "${YELLOW}Logs for $SERVICE (last $LINES lines):${NC}"
    kubectl logs $POD -n $NAMESPACE --tail=$LINES
}

# Function to follow logs
follow_logs() {
    SERVICE=$1

    if [ -z "$SERVICE" ]; then
        echo -e "${RED}Error: Service name required${NC}"
        return 1
    fi

    POD=$(kubectl get pods -n $NAMESPACE -l app=$SERVICE -o jsonpath='{.items[0].metadata.name}' 2>/dev/null)

    if [ -z "$POD" ]; then
        echo -e "${RED}Error: No pod found for $SERVICE${NC}"
        return 1
    fi

    echo -e "${YELLOW}Following logs for $SERVICE (Ctrl+C to stop):${NC}"
    kubectl logs -f $POD -n $NAMESPACE
}

# Function to describe service
describe_service() {
    SERVICE=$1

    if [ -z "$SERVICE" ]; then
        echo -e "${RED}Error: Service name required${NC}"
        return 1
    fi

    echo -e "${YELLOW}Deployment Info:${NC}"
    kubectl describe deployment/$SERVICE -n $NAMESPACE
    echo ""
    echo -e "${YELLOW}Pod Info:${NC}"
    kubectl describe pod -l app=$SERVICE -n $NAMESPACE
    echo ""
    echo -e "${YELLOW}Service Info:${NC}"
    kubectl describe svc $SERVICE -n $NAMESPACE
}

# Function to get deployment history
deployment_history() {
    SERVICE=$1

    if [ -z "$SERVICE" ]; then
        echo -e "${RED}Error: Service name required${NC}"
        return 1
    fi

    echo -e "${YELLOW}Deployment history for $SERVICE:${NC}"
    kubectl rollout history deployment/$SERVICE -n $NAMESPACE
}

# Function to rollback
rollback_service() {
    SERVICE=$1
    REVISION=$2

    if [ -z "$SERVICE" ]; then
        echo -e "${RED}Error: Service name required${NC}"
        return 1
    fi

    if [ -z "$REVISION" ]; then
        echo -e "${YELLOW}Rolling back $SERVICE to previous revision...${NC}"
        kubectl rollout undo deployment/$SERVICE -n $NAMESPACE
    else
        echo -e "${YELLOW}Rolling back $SERVICE to revision $REVISION...${NC}"
        kubectl rollout undo deployment/$SERVICE --to-revision=$REVISION -n $NAMESPACE
    fi

    kubectl rollout status deployment/$SERVICE -n $NAMESPACE --timeout=300s
    echo -e "${GREEN}✓ $SERVICE rolled back${NC}"
}

# Main menu
show_menu() {
    echo ""
    echo -e "${BLUE}============================================${NC}"
    echo -e "${BLUE}Service Management Menu${NC}"
    echo -e "${BLUE}============================================${NC}"
    echo ""
    echo "1. Show status"
    echo "2. Restart service"
    echo "3. Scale service"
    echo "4. View logs"
    echo "5. Follow logs (tail -f)"
    echo "6. Describe service"
    echo "7. Deployment history"
    echo "8. Rollback service"
    echo "9. Exit"
    echo ""
    read -p "Select option: " option

    case $option in
        1)
            show_status
            show_menu
            ;;
        2)
            read -p "Service name: " service
            restart_service $service
            show_menu
            ;;
        3)
            read -p "Service name: " service
            read -p "Number of replicas: " replicas
            scale_service $service $replicas
            show_menu
            ;;
        4)
            read -p "Service name: " service
            read -p "Number of lines (default 50): " lines
            view_logs $service ${lines:-50}
            show_menu
            ;;
        5)
            read -p "Service name: " service
            follow_logs $service
            show_menu
            ;;
        6)
            read -p "Service name: " service
            describe_service $service
            show_menu
            ;;
        7)
            read -p "Service name: " service
            deployment_history $service
            show_menu
            ;;
        8)
            read -p "Service name: " service
            read -p "Revision (empty for previous): " revision
            rollback_service $service $revision
            show_menu
            ;;
        9)
            echo -e "${GREEN}Goodbye!${NC}"
            exit 0
            ;;
        *)
            echo -e "${RED}Invalid option${NC}"
            show_menu
            ;;
    esac
}

# Check if command line arguments provided
if [ $# -eq 0 ]; then
    show_status
    show_menu
else
    # Command line mode
    COMMAND=$1
    shift

    case $COMMAND in
        status)
            show_status
            ;;
        restart)
            restart_service $@
            ;;
        scale)
            scale_service $@
            ;;
        logs)
            view_logs $@
            ;;
        follow)
            follow_logs $@
            ;;
        describe)
            describe_service $@
            ;;
        history)
            deployment_history $@
            ;;
        rollback)
            rollback_service $@
            ;;
        *)
            echo -e "${RED}Unknown command: $COMMAND${NC}"
            echo ""
            echo -e "${YELLOW}Usage:${NC}"
            echo "  $0                                    # Interactive menu"
            echo "  $0 status                             # Show status"
            echo "  $0 restart <service>                  # Restart service"
            echo "  $0 scale <service> <replicas>         # Scale service"
            echo "  $0 logs <service> [lines]             # View logs"
            echo "  $0 follow <service>                   # Follow logs"
            echo "  $0 describe <service>                 # Describe service"
            echo "  $0 history <service>                  # Deployment history"
            echo "  $0 rollback <service> [revision]      # Rollback"
            exit 1
            ;;
    esac
fi
